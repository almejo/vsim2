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
import cl.almejo.vsim.core.logic.GeneratorOptions.GateStyle;
import cl.almejo.vsim.core.logic.Minimization.Form;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableEvaluator;
import cl.almejo.vsim.core.truthtable.TruthTableScope;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class CircuitGeneratorTest {

    private static final List<String> INPUT_NAMES = List.of("A", "B", "C", "D", "E", "F", "G", "H");
    private static final List<String> OUTPUT_NAMES = List.of("P", "Q", "R", "S", "T", "U", "V", "W");
    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();
    /** Random tables up to this many inputs also go through the simulator (it is slow on 6..8 inputs). */
    private static final int SIMULATED_INPUTS = 5;

    @Test
    void theBcdExampleBuildsTheCircuitOfTheTalk() {
        TruthTableSpec spec = bcd();
        Minimization minimization = Minimizer.minimize(spec, Form.SUM_OF_PRODUCTS);

        GeneratedCircuit circuit = CircuitGenerator.generate(spec, minimization, GeneratorOptions.DEFAULTS, REGISTRY);

        // P = B'C + BD, M = A + BD + BC with BD shared: one NOT (B'), three AND2, an OR2 and an OR3.
        assertThat(circuit.gatesByType()).containsExactly(Map.entry("and2", 3), Map.entry("or2", 1),
                Map.entry("or3", 1), Map.entry("not", 1));
        assertThat(circuit.gateCount()).isEqualTo(6);
        assertThat(circuit.cost().familyCounts()).containsExactly(Map.entry("AND", 3), Map.entry("OR", 2),
                Map.entry("NOT", 1));
        assertMatches(spec, circuit);
    }

    @Test
    void pinsCarryTheColumnNamesInTableOrderAndTheFrameEnclosesEverything() {
        TruthTableSpec spec = new TruthTableSpec("Mi tabla", List.of("Entrada uno", "b", "Temperatura alta"),
                List.of("Alarma", "z"), List.of(values(3, 1, 0, 1, 1, 0, 0, 1, 0), values(3, 0, 1, 1, 0, 1, 1, 0, 2)));

        GeneratedCircuit circuit = CircuitGenerator.generate(spec, Minimizer.minimize(spec, Form.SUM_OF_PRODUCTS),
                GeneratorOptions.DEFAULTS, REGISTRY);

        CircuitFragment fragment = circuit.fragment();
        assertThat(sortedLabels(fragment, StandardComponentTypes.INPUT_PIN))
                .containsExactly("Entrada uno", "b", "Temperatura alta");
        assertThat(sortedLabels(fragment, StandardComponentTypes.OUTPUT_PIN)).containsExactly("Alarma", "z");
        assertThat(fragment.frames()).hasSize(1);
        Frame frame = fragment.frames().get(0);
        assertThat(frame.title()).isEqualTo("Mi tabla");
        assertThat(frame.locked()).isTrue();
        assertEnclosed(fragment);
        assertMatches(spec, circuit);
    }

    @Test
    void randomNetworksMatchTheirTableInEveryStyleFormAndSharing() {
        // The gate network itself, evaluated row by row without the simulator: hundreds of tables in well under a
        // second. Circuits built from networks go through the simulator in the other tests, on chosen tables.
        Random random = new Random(20261019L);
        int checked = 0;
        for (int round = 0; round < 200; round++) {
            TruthTableSpec spec = randomSpec(random, 1 + random.nextInt(8), 1 + random.nextInt(8), 0.6);
            for (Form form : Form.values()) {
                Minimization minimization = Minimizer.minimize(spec, form);
                for (GateStyle style : GateStyle.values()) {
                    for (boolean share : List.of(true, false)) {
                        GeneratorOptions options = new GeneratorOptions(style, form, share);
                        LogicNetwork network = LogicNetwork.of(minimization, options);
                        assertNetworkMatches(spec, network, options);
                        assertThat(CircuitGenerator.cost(minimization, options)).isEqualTo(network.cost());
                        checked++;
                    }
                }
            }
        }
        assertThat(checked).isEqualTo(200 * 12);
    }

    @Test
    void randomTablesGiveCleanLayoutsAndTheSmallOnesMatchThroughTheSimulator() {
        Random random = new Random(20261019L);
        for (int round = 0; round < 12; round++) {
            int inputs = 2 + random.nextInt(7);
            int outputs = 1 + random.nextInt(inputs > 6 ? 3 : 8);
            TruthTableSpec spec = randomSpec(random, inputs, outputs);
            for (GateStyle style : GateStyle.values()) {
                Form form = random.nextBoolean() ? Form.SUM_OF_PRODUCTS : Form.PRODUCT_OF_SUMS;
                GeneratorOptions options = new GeneratorOptions(style, form, random.nextBoolean());
                if (inputs <= SIMULATED_INPUTS) {
                    checkEverything(spec, options);
                } else {
                    checkLayoutAndNetwork(spec, options);
                }
            }
        }
    }

    @Test
    void everyOptionCombinationWorksOnSmallAndLargeTables() {
        Random random = new Random(7L);
        List<TruthTableSpec> specs = List.of(randomSpec(random, 2, 3), randomSpec(random, 4, 4),
                randomSpec(random, 6, 5), randomSpec(random, 1, 2));
        for (TruthTableSpec spec : specs) {
            for (GateStyle style : GateStyle.values()) {
                for (Form form : Form.values()) {
                    for (boolean share : List.of(true, false)) {
                        GeneratorOptions options = new GeneratorOptions(style, form, share);
                        if (spec.inputCount() <= SIMULATED_INPUTS) {
                            checkEverything(spec, options);
                        } else {
                            checkLayoutAndNetwork(spec, options);
                        }
                    }
                }
            }
        }
    }

    @Test
    void chosenLargeTablesMatchThroughTheSimulator() {
        // A 4-bit adder (8 inputs, 5 outputs) and 8-input parity without sharing (128 terms of 8 literals, deep
        // NAND trees): the worst cases of the import, through the real simulator once each.
        checkEverything(adder(), GeneratorOptions.DEFAULTS);
        checkEverything(parity(), new GeneratorOptions(GateStyle.NAND_ONLY, Form.SUM_OF_PRODUCTS, false));
    }

    @Test
    void aFullEightByEightTableFitsWithoutLayoutProblems() {
        // The import's largest table, irregular on purpose (a third of the cells X): hundreds of gates.
        TruthTableSpec spec = randomSpec(new Random(88L), 8, 8);
        checkEverything(spec, GeneratorOptions.DEFAULTS);
    }

    @Test
    void constantOutputsAreFedByConstants() {
        // Z always 0, O always 1, X always X, Y a single 1 with the rest X (1 after minimizing).
        TruthTableSpec spec = new TruthTableSpec("Constantes", List.of("A", "B"), List.of("Z", "O", "X", "Y"),
                List.of(values(2, 0, 0, 0, 0), values(2, 1, 1, 1, 1), values(2, 2, 2, 2, 2), values(2, 2, 1, 2, 2)));
        for (GateStyle style : GateStyle.values()) {
            for (Form form : Form.values()) {
                GeneratedCircuit circuit = checkEverything(spec, new GeneratorOptions(style, form, true));
                assertThat(circuit.gateCount()).isZero();
                assertThat(circuit.fragment().components())
                        .filteredOn(component -> component.typeId().equals(StandardComponentTypes.CONSTANT))
                        .isNotEmpty()
                        .allSatisfy(component -> assertThat(component.parameters().get("width")).isEqualTo(1));
            }
        }
    }

    @Test
    void oneLiteralAndOneTermOutputsSkipTheGates() {
        // F = A (straight wire), G = B' (only the NOT of B), H = AB (one AND, no OR).
        TruthTableSpec spec = new TruthTableSpec("T", List.of("A", "B"), List.of("F", "G", "H"),
                List.of(values(2, 0, 0, 1, 1), values(2, 1, 0, 1, 0), values(2, 0, 0, 0, 1)));

        GeneratedCircuit circuit = checkEverything(spec, GeneratorOptions.DEFAULTS);

        assertThat(circuit.gatesByType()).containsExactly(Map.entry("and2", 1), Map.entry("not", 1));
        GeneratedCircuit nand = checkEverything(spec,
                new GeneratorOptions(GateStyle.NAND_ONLY, Form.SUM_OF_PRODUCTS, true));
        // B' and the AND's inversion are NAND2 with tied inputs; AB itself is a NAND2: three NAND2.
        assertThat(nand.gatesByType()).containsExactly(Map.entry("nand2", 3));
    }

    @Test
    void wideTermsAreSplitIntoTreesOfSmallGates() {
        List<LogicValue> onlyLastRow = new ArrayList<>(Collections.nCopies(256, LogicValue.ZERO));
        onlyLastRow.set(255, LogicValue.ONE);
        TruthTableSpec spec = new TruthTableSpec("Ocho", INPUT_NAMES, List.of("Todas"), List.of(onlyLastRow));

        GeneratedCircuit circuit = checkEverything(spec, GeneratorOptions.DEFAULTS);

        // ABCDEFGH: two AND4 joined by an AND2.
        assertThat(circuit.gatesByType()).containsExactly(Map.entry("and2", 1), Map.entry("and4", 2));
        GeneratedCircuit sixLiterals = checkEverything(new TruthTableSpec("Seis", INPUT_NAMES.subList(0, 6),
                List.of("Y"), List.of(onlyLastRow.subList(192, 256))), GeneratorOptions.DEFAULTS);
        assertThat(sixLiterals.gatesByType()).containsExactly(Map.entry("and2", 1), Map.entry("and3", 2));
    }

    @Test
    void sharingUsesOneGatePerDistinctTerm() {
        // F = AB + C, G = AB + C': AB appears in both.
        TruthTableSpec spec = new TruthTableSpec("T", List.of("A", "B", "C"), List.of("F", "G"),
                List.of(values(3, 0, 1, 0, 1, 0, 1, 1, 1), values(3, 1, 0, 1, 0, 1, 0, 1, 1)));

        GeneratedCircuit shared = checkEverything(spec, GeneratorOptions.DEFAULTS);
        GeneratedCircuit separate = checkEverything(spec,
                new GeneratorOptions(GateStyle.AND_OR_NOT, Form.SUM_OF_PRODUCTS, false));

        assertThat(shared.gatesByType().get("and2")).isEqualTo(1);
        assertThat(separate.gatesByType().get("and2")).isEqualTo(2);
    }

    @Test
    void theCostIsTheGateCountOfTheBuiltCircuit() {
        TruthTableSpec spec = bcd();
        for (GateStyle style : GateStyle.values()) {
            for (Form form : Form.values()) {
                GeneratorOptions options = new GeneratorOptions(style, form, true);
                Minimization minimization = Minimizer.minimize(spec, form);
                GateCost cost = CircuitGenerator.cost(minimization, options);
                GeneratedCircuit circuit = CircuitGenerator.generate(spec, minimization, options, REGISTRY);
                assertThat(cost.gatesByType()).isEqualTo(circuit.gatesByType());
                assertThat(cost.gateCount()).isEqualTo(circuit.gateCount());
            }
        }
    }

    @Test
    void theSameTableAlwaysGivesTheSameCircuit() {
        TruthTableSpec spec = randomSpec(new Random(3L), 6, 4);
        Minimization minimization = Minimizer.minimize(spec, Form.SUM_OF_PRODUCTS);

        CircuitFragment first = CircuitGenerator.generate(spec, minimization, GeneratorOptions.DEFAULTS, REGISTRY)
                .fragment();
        CircuitFragment second = CircuitGenerator.generate(spec, minimization, GeneratorOptions.DEFAULTS, REGISTRY)
                .fragment();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void aMinimizationOfTheOtherFormIsRefused() {
        TruthTableSpec spec = bcd();
        Minimization products = Minimizer.minimize(spec, Form.PRODUCT_OF_SUMS);

        assertThatThrownBy(() -> CircuitGenerator.generate(spec, products, GeneratorOptions.DEFAULTS, REGISTRY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CircuitGenerator.cost(products, GeneratorOptions.DEFAULTS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---

    /** {@link #checkLayoutAndNetwork}, then the circuit's truth table through the simulator. */
    private static GeneratedCircuit checkEverything(TruthTableSpec spec, GeneratorOptions options) {
        GeneratedCircuit circuit = checkLayoutAndNetwork(spec, options);
        assertMatches(spec, circuit);
        return circuit;
    }

    /** The layout is clean, the cost is the built gates, and the gate network gives the table (no simulator). */
    private static GeneratedCircuit checkLayoutAndNetwork(TruthTableSpec spec, GeneratorOptions options) {
        Minimization minimization = Minimizer.minimize(spec, options.form());
        GeneratedCircuit circuit = CircuitGenerator.generate(spec, minimization, options, REGISTRY);
        String context = options + " on " + spec.inputCount() + "x" + spec.outputCount() + " " + spec.outputs();
        CircuitDocument document = open(circuit.fragment());
        assertThat(ExampleChecks.layoutProblems(document, REGISTRY)).as(context).isEmpty();
        assertThat(new NetlistCompiler(REGISTRY).compile(document).nets()).as(context)
                .allSatisfy(net -> assertThat(net.pins()).as("dangling wire").isNotEmpty());
        assertEnclosed(circuit.fragment());
        assertThat(circuit.fragment().frames()).as(context).hasSize(1);
        assertThat(CircuitGenerator.cost(minimization, options)).as(context).isEqualTo(circuit.cost());
        assertThat(countGates(circuit.fragment())).as(context).isEqualTo(circuit.gatesByType());
        assertNetworkMatches(spec, LogicNetwork.of(minimization, options), options);
        return circuit;
    }

    /**
     * Evaluates {@code network} without the simulator and compares each output with the table, X rows aside; also
     * checks each gate's fan-in and that a style uses only its own gates. Every signal is a bit set over the rows
     * (bit {@code row} is its value on that row), so one pass over the gates (sources come first) evaluates all
     * rows at once.
     */
    private static void assertNetworkMatches(TruthTableSpec spec, LogicNetwork network, GeneratorOptions options) {
        Set<String> families = switch (options.gates()) {
            case NAND_ONLY -> Set.of("nand");
            case NOR_ONLY -> Set.of("nor");
            case AND_OR_NOT -> Set.of("and", "or", "not");
        };
        int words = (spec.rowCount() + Long.SIZE - 1) / Long.SIZE;
        long[][] gateValues = new long[network.gates().size()][];
        for (int index = 0; index < gateValues.length; index++) {
            LogicNetwork.Gate gate = network.gates().get(index);
            String family = gate.typeId().replaceAll("\\d", "");
            if (!families.contains(family) || gate.inputs().isEmpty()
                    || gate.inputs().size() > LogicNetwork.MAXIMUM_FAN_IN) {
                fail("%s: gate %s with %d inputs", options, gate.typeId(), gate.inputs().size());
            }
            boolean conjunction = family.equals("and") || family.equals("nand") || family.equals("not");
            boolean negated = !family.equals("and") && !family.equals("or");
            long[] value = new long[words];
            Arrays.fill(value, conjunction ? -1L : 0L);
            for (LogicNetwork.Signal input : gate.inputs()) {
                long[] operand = signalValue(input, spec, gateValues);
                for (int word = 0; word < words; word++) {
                    value[word] = conjunction ? value[word] & operand[word] : value[word] | operand[word];
                }
            }
            if (negated) {
                for (int word = 0; word < words; word++) {
                    value[word] = ~value[word];
                }
            }
            gateValues[index] = value;
        }
        for (int output = 0; output < spec.outputCount(); output++) {
            long[] actual = signalValue(network.outputs().get(output), spec, gateValues);
            for (int row = 0; row < spec.rowCount(); row++) {
                LogicValue expected = spec.value(output, row);
                boolean value = (actual[row / Long.SIZE] >>> (row % Long.SIZE) & 1) == 1;
                if (expected != LogicValue.DONT_CARE && value != (expected == LogicValue.ONE)) {
                    fail("%s: output %d row %d of %s", options, output, row, spec.outputs());
                }
            }
        }
    }

    private static long[] signalValue(LogicNetwork.Signal signal, TruthTableSpec spec, long[][] gateValues) {
        int words = (spec.rowCount() + Long.SIZE - 1) / Long.SIZE;
        return switch (signal) {
            case LogicNetwork.GateSignal gate -> gateValues[gate.gate()];
            case LogicNetwork.ConstantSignal constant -> {
                long[] value = new long[words];
                Arrays.fill(value, constant.value() ? -1L : 0L);
                yield value;
            }
            case LogicNetwork.InputSignal input -> {
                long[] value = new long[words];
                int shift = spec.inputCount() - 1 - input.input();
                for (int row = 0; row < spec.rowCount(); row++) {
                    if ((row >> shift & 1) == 1) {
                        value[row / Long.SIZE] |= 1L << (row % Long.SIZE);
                    }
                }
                yield value;
            }
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

    private static void assertMatches(TruthTableSpec spec, GeneratedCircuit circuit) {
        CircuitDocument document = open(circuit.fragment());
        Preparation preparation = TruthTableScope.prepare(document, Set.of(), REGISTRY);
        assertThat(preparation).isInstanceOf(Preparation.Ready.class);
        Preparation.Ready ready = (Preparation.Ready) preparation;
        assertThat(ready.inputs().stream().map(Column::name).toList()).isEqualTo(spec.inputNames());
        assertThat(ready.outputs().stream().map(Column::name).toList()).isEqualTo(spec.outputNames());
        TruthTable table = TruthTableEvaluator.evaluate(ready, percent -> {
        }, () -> false).orElseThrow();
        for (int row = 0; row < spec.rowCount(); row++) {
            Row computed = table.rows().get(row);
            assertThat(computed.settled()).isTrue();
            for (int output = 0; output < spec.outputCount(); output++) {
                LogicValue expected = spec.value(output, row);
                if (expected != LogicValue.DONT_CARE) {
                    assertThat(computed.outputs().get(output))
                            .as("output %s row %d", spec.outputNames().get(output), row)
                            .isEqualTo(Value.of(expected == LogicValue.ONE));
                }
            }
        }
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
        // The pins' labels are drawn above them: the frame leaves room for them under its title bar.
        int highestPin = fragment.components().stream().mapToInt(component -> component.position().y()).min()
                .orElseThrow();
        assertThat(highestPin - frame.topLeft().y()).isGreaterThanOrEqualTo(4);
    }

    private static List<String> sortedLabels(CircuitFragment fragment, String typeId) {
        return fragment.components().stream()
                .filter(component -> component.typeId().equals(typeId))
                .sorted(Comparator.comparingInt((Component component) -> component.position().y()))
                .map(Component::label)
                .toList();
    }

    private static TruthTableSpec bcd() {
        List<Integer> notDigits = List.of(10, 11, 12, 13, 14, 15);
        return new TruthTableSpec("BCD", INPUT_NAMES.subList(0, 4), List.of("P", "M"),
                List.of(column(16, List.of(2, 3, 5, 7), notDigits), column(16, List.of(5, 6, 7, 8, 9), notDigits)));
    }

    /** 4-bit A + B (A = inputs A..D, B = E..H): the sum S3..S0 and the carry Cout. */
    private static TruthTableSpec adder() {
        List<List<LogicValue>> columns = new ArrayList<>();
        for (int bit = 3; bit >= -1; bit--) {
            List<LogicValue> column = new ArrayList<>();
            for (int row = 0; row < 256; row++) {
                int sum = (row >> 4) + (row & 0xF);
                column.add((sum >> (bit < 0 ? 4 : bit) & 1) == 1 ? LogicValue.ONE : LogicValue.ZERO);
            }
            columns.add(column);
        }
        return new TruthTableSpec("Sumador", INPUT_NAMES, List.of("S3", "S2", "S1", "S0", "Cout"), columns);
    }

    /** The parity of 8 inputs: 128 minterms, no two of which combine. */
    private static TruthTableSpec parity() {
        List<LogicValue> column = new ArrayList<>();
        for (int row = 0; row < 256; row++) {
            column.add(Integer.bitCount(row) % 2 == 1 ? LogicValue.ONE : LogicValue.ZERO);
        }
        return new TruthTableSpec("Paridad", INPUT_NAMES, List.of("Impar"), List.of(column));
    }

    private static TruthTableSpec randomSpec(Random random, int inputs, int outputs) {
        return randomSpec(random, inputs, outputs, 0.3);
    }

    private static TruthTableSpec randomSpec(Random random, int inputs, int outputs, double maximumDontCare) {
        double dontCare = random.nextDouble() * maximumDontCare;
        double ones = 0.2 + random.nextDouble() * 0.6;
        List<List<LogicValue>> columns = new ArrayList<>();
        for (int output = 0; output < outputs; output++) {
            List<LogicValue> column = new ArrayList<>();
            for (int row = 0; row < 1 << inputs; row++) {
                double draw = random.nextDouble();
                column.add(draw < dontCare ? LogicValue.DONT_CARE
                        : random.nextDouble() < ones ? LogicValue.ONE : LogicValue.ZERO);
            }
            columns.add(column);
        }
        return new TruthTableSpec("Tabla", INPUT_NAMES.subList(0, inputs), OUTPUT_NAMES.subList(0, outputs),
                columns);
    }

    private static List<LogicValue> column(int rows, List<Integer> ones, List<Integer> dontCares) {
        List<LogicValue> values = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            values.add(ones.contains(row) ? LogicValue.ONE
                    : dontCares.contains(row) ? LogicValue.DONT_CARE : LogicValue.ZERO);
        }
        return values;
    }

    /** Values written as 0, 1 and 2 (X). */
    private static List<LogicValue> values(int inputs, int... cells) {
        assertThat(cells).hasSize(1 << inputs);
        List<LogicValue> values = new ArrayList<>();
        for (int cell : cells) {
            values.add(LogicValue.values()[cell]);
        }
        return values;
    }
}
