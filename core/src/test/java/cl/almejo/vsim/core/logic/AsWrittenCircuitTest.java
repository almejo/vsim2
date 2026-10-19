package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.bundled.ExampleChecks;
import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class AsWrittenCircuitTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();
    /** Random lines up to this many inputs also go through the simulator (it is slow on 6..8 inputs). */
    private static final int SIMULATED_INPUTS = 5;

    @Test
    void randomExpressionsGiveCleanCircuitsThatMatchTheirTable() {
        Random random = new Random(7);
        int built = 0;
        for (int trial = 0; built < 120 && trial < 1000; trial++) {
            int inputCount = 1 + trial % ExpressionTable.MAXIMUM_INPUTS;
            List<String> names = new ArrayList<>();
            for (int input = 0; input < inputCount; input++) {
                names.add(String.valueOf((char) ('A' + input)));
            }
            int outputCount = 1 + random.nextInt(3);
            List<ExpressionLine> lines = new ArrayList<>();
            for (int output = 0; output < outputCount; output++) {
                lines.add(new ExpressionLine("Y" + output, RandomExpressions.of(random, names, 5)));
            }
            ExpressionTable.Result table = ExpressionTable.of("Aleatorio", lines);
            if (table.spec().isEmpty()) {
                continue;
            }
            built++;
            TruthTableSpec spec = table.spec().get();
            boolean simulate = spec.inputCount() <= SIMULATED_INPUTS || built % 10 == 0;
            checkEverything(spec, lines, true, simulate);
            checkEverything(spec, lines, false, simulate);
        }
        assertThat(built).isEqualTo(120);
    }

    @Test
    void chosenExpressionsMatchThroughTheSimulator() {
        for (String text : List.of("F = A + B C ^ D", "F = !A'", "F = (A+B)'C", "F = A 1 + B 0",
                "F = 1' + A", "F = A ^ B ^ C ^ D ^ E ^ G", "F = (A B)' (A B)' + (A B)'",
                "F = ((A + B)' (C ^ D))' ^ (E + G' + H)")) {
            List<ExpressionLine> lines = lines(text);
            checkEverything(ExpressionTable.of("T", lines).spec().orElseThrow(), lines, true, true);
        }
        List<ExpressionLine> adder = lines("S = A ^ B ^ Cin", "Cout = A B + Cin (A ^ B)");
        checkEverything(ExpressionTable.of("Sumador", adder).spec().orElseThrow(), adder, true, true);
        checkEverything(ExpressionTable.of("Sumador", adder).spec().orElseThrow(), adder, false, true);
    }

    @Test
    void eachOperatorIsOneGate() {
        assertThat(cost("F = A B' + C", true).gatesByType())
                .containsExactly(Map.entry("and2", 1), Map.entry("or2", 1), Map.entry("not", 1));
        assertThat(cost("F = A ^ B ^ C", true).gatesByType()).containsExactly(Map.entry("xor3", 1));
        assertThat(cost("F = (A + B)'", true).gatesByType())
                .containsExactly(Map.entry("or2", 1), Map.entry("not", 1));
    }

    @Test
    void wideOperationsBecomeTrees() {
        // Five operands: groups of 3 and 2, then an AND2 of both.
        assertThat(cost("F = A B C D E", true).gatesByType())
                .containsExactly(Map.entry("and2", 2), Map.entry("and3", 1));
        assertThat(cost("F = A ^ B ^ C ^ D ^ E ^ G ^ H ^ I", true).gatesByType())
                .containsExactly(Map.entry("xor2", 1), Map.entry("xor4", 2));
    }

    @Test
    void notOfAVariableIsTheSharedInputInverter() {
        List<ExpressionLine> lines = lines("F = A' B", "G = A' C");
        TruthTableSpec spec = ExpressionTable.of("T", lines).spec().orElseThrow();
        assertThat(CircuitGenerator.costAsWritten(spec, lines, false).gatesByType())
                .containsExactly(Map.entry("and2", 2), Map.entry("not", 1));
        // A double NOT is two inverters: the input's and one after it.
        assertThat(cost("F = !A' + B", true).gatesByType())
                .containsExactly(Map.entry("or2", 1), Map.entry("not", 2));
    }

    @Test
    void sharingUsesOneGatePerEqualSubExpression() {
        List<ExpressionLine> lines = lines("F = A B + C", "G = A B + D'", "H = (A B)' ^ (A B)'");
        TruthTableSpec spec = ExpressionTable.of("T", lines).spec().orElseThrow();
        // Shared: AND2(A, B), the two OR2, NOT D, NOT(AB), XOR2.
        assertThat(CircuitGenerator.costAsWritten(spec, lines, true).gateCount()).isEqualTo(6);
        // Not shared between outputs: AND2 in each line, the same inside one line (H uses one AND2 and one NOT).
        assertThat(CircuitGenerator.costAsWritten(spec, lines, false).gateCount()).isEqualTo(8);
    }

    @Test
    void aVariableOutputIsTheInputAndAConstantOutputIsAConstant() {
        List<ExpressionLine> lines = lines("F = A", "G = 1", "H = 0", "I = A + 1");
        TruthTableSpec spec = ExpressionTable.of("T", lines).spec().orElseThrow();
        GeneratedCircuit circuit = checkEverything(spec, lines, true, true);
        assertThat(circuit.gatesByType()).containsExactly(Map.entry("or2", 1));
        // Shared: one 1 for G and I, one 0 for H; not shared: one constant per use.
        assertThat(constants(circuit)).isEqualTo(2);
        assertThat(constants(checkEverything(spec, lines, false, true))).isEqualTo(3);
        List<ExpressionLine> twoOnes = lines("F = A + 1", "G = B 1");
        TruthTableSpec twoOnesSpec = ExpressionTable.of("T", twoOnes).spec().orElseThrow();
        assertThat(constants(checkEverything(twoOnesSpec, twoOnes, true, true))).isEqualTo(1);
        assertThat(constants(checkEverything(twoOnesSpec, twoOnes, false, true))).isEqualTo(2);
    }

    @Test
    void theCostLineNamesTheXorFamily() {
        GateCost cost = cost("F = (A ^ B) C + D'", true);
        assertThat(cost.familyCounts()).containsExactly(Map.entry("AND", 1), Map.entry("OR", 1),
                Map.entry("XOR", 1), Map.entry("NOT", 1));
        assertThat(GateCost.FAMILIES).containsExactly("AND", "OR", "XOR", "NOT", "NAND", "NOR");
    }

    @Test
    void theSameLinesAlwaysGiveTheSameCircuit() {
        List<ExpressionLine> lines = lines("F = A + B C ^ D", "G = (A + B)' C");
        TruthTableSpec spec = ExpressionTable.of("T", lines).spec().orElseThrow();
        assertThat(CircuitGenerator.generateAsWritten(spec, lines, true, REGISTRY))
                .isEqualTo(CircuitGenerator.generateAsWritten(spec, lines, true, REGISTRY));
    }

    @Test
    void aTableOfOtherLinesIsRefused() {
        List<ExpressionLine> lines = lines("F = A B");
        TruthTableSpec other = ExpressionTable.of("T", lines("G = A B")).spec().orElseThrow();
        assertThatThrownBy(() -> CircuitGenerator.generateAsWritten(other, lines, true, REGISTRY))
                .isInstanceOf(IllegalArgumentException.class);
        TruthTableSpec fewerInputs = ExpressionTable.of("T", lines("F = A")).spec().orElseThrow();
        assertThatThrownBy(() -> CircuitGenerator.costAsWritten(fewerInputs, lines, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---

    private static GateCost cost(String text, boolean shareTerms) {
        List<ExpressionLine> lines = lines(text);
        TruthTableSpec spec = ExpressionTable.of("T", lines).spec().orElseThrow();
        GeneratedCircuit circuit = checkEverything(spec, lines, shareTerms, false);
        return circuit.cost();
    }

    private static long constants(GeneratedCircuit circuit) {
        return circuit.fragment().components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.CONSTANT)).count();
    }

    /**
     * The layout is clean, the cost is the built gates, the gate network gives the table and, when
     * {@code simulate}, the circuit passes the ✓ check through the simulator.
     */
    private static GeneratedCircuit checkEverything(TruthTableSpec spec, List<ExpressionLine> lines,
                                                    boolean shareTerms, boolean simulate) {
        GeneratedCircuit circuit = CircuitGenerator.generateAsWritten(spec, lines, shareTerms, REGISTRY);
        String context = lines + " sharing " + shareTerms;
        CircuitDocument document = open(circuit.fragment());
        assertThat(ExampleChecks.layoutProblems(document, REGISTRY)).as(context).isEmpty();
        assertThat(new NetlistCompiler(REGISTRY).compile(document).nets()).as(context)
                .allSatisfy(net -> assertThat(net.pins()).as("dangling wire").isNotEmpty());
        assertEnclosed(circuit.fragment());
        assertThat(circuit.fragment().frames()).as(context).hasSize(1);
        assertThat(CircuitGenerator.costAsWritten(spec, lines, shareTerms)).as(context).isEqualTo(circuit.cost());
        assertThat(countGates(circuit.fragment())).as(context).isEqualTo(circuit.gatesByType());
        assertNetworkMatches(spec, LogicNetwork.ofExpressions(spec, lines, shareTerms), context);
        if (simulate) {
            TableCircuits.Check check = TableCircuits.check(spec, circuit.fragment(), REGISTRY);
            assertThat(check.passed()).as(context + " " + check).isTrue();
        }
        return circuit;
    }

    /** Evaluates the gates row by row without the simulator; also checks the families and the fan-in. */
    private static void assertNetworkMatches(TruthTableSpec spec, LogicNetwork network, String context) {
        for (LogicNetwork.Gate gate : network.gates()) {
            String family = GateCost.family(gate.typeId());
            int expectedInputs = family.equals("not") ? 1 : 2;
            if (!List.of("and", "or", "xor", "not").contains(family) || gate.inputs().size() < expectedInputs
                    || gate.inputs().size() > LogicNetwork.MAXIMUM_FAN_IN) {
                fail("%s: gate %s with %d inputs", context, gate.typeId(), gate.inputs().size());
            }
        }
        for (int row = 0; row < spec.rowCount(); row++) {
            boolean[] gateValues = new boolean[network.gates().size()];
            for (int index = 0; index < gateValues.length; index++) {
                LogicNetwork.Gate gate = network.gates().get(index);
                String family = GateCost.family(gate.typeId());
                boolean value = family.equals("and");
                for (LogicNetwork.Signal input : gate.inputs()) {
                    boolean operand = signalValue(input, spec, row, gateValues);
                    value = switch (family) {
                        case "and" -> value & operand;
                        case "or" -> value | operand;
                        case "xor" -> value ^ operand;
                        default -> !operand;
                    };
                }
                gateValues[index] = value;
            }
            for (int output = 0; output < spec.outputCount(); output++) {
                boolean actual = signalValue(network.outputs().get(output), spec, row, gateValues);
                assertThat(actual ? LogicValue.ONE : LogicValue.ZERO).as("%s output %d row %d", context, output, row)
                        .isEqualTo(spec.value(output, row));
            }
        }
    }

    private static boolean signalValue(LogicNetwork.Signal signal, TruthTableSpec spec, int row,
                                       boolean[] gateValues) {
        return switch (signal) {
            case LogicNetwork.GateSignal gate -> gateValues[gate.gate()];
            case LogicNetwork.ConstantSignal constant -> constant.value();
            case LogicNetwork.InputSignal input -> spec.inputValue(row, input.input());
        };
    }

    private static Map<String, Integer> countGates(CircuitFragment fragment) {
        Map<String, Integer> counts = new HashMap<>();
        for (Component component : fragment.components()) {
            String type = component.typeId();
            if (!type.equals(StandardComponentTypes.INPUT_PIN) && !type.equals(StandardComponentTypes.OUTPUT_PIN)
                    && !type.equals(StandardComponentTypes.CONSTANT)) {
                counts.merge(type, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static CircuitDocument open(CircuitFragment fragment) {
        CircuitDocument document = new CircuitDocument();
        new PasteCommand(fragment, new GridPoint(0, 0)).apply(document);
        return document;
    }

    private static void assertEnclosed(CircuitFragment fragment) {
        Frame frame = fragment.frames().get(0);
        List<DocumentElement> inside = new ArrayList<>(fragment.components());
        inside.addAll(fragment.wires());
        inside.addAll(fragment.labels());
        for (DocumentElement element : inside) {
            Bounds bounds = ElementBounds.of(element, REGISTRY);
            assertThat(frame.encloses(bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                    bounds.y() + bounds.height())).as("frame encloses %s", element).isTrue();
        }
    }

    private static List<ExpressionLine> lines(String... texts) {
        List<ExpressionLine> lines = new ArrayList<>();
        for (String text : texts) {
            lines.add(ExpressionParser.parseLine(text).line().orElseThrow());
        }
        return lines;
    }
}
