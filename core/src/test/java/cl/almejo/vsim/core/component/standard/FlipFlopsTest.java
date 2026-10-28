package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.GateHarness;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The «Secuenciales» flip-flops: D (with Q̅ appended as its last pin), JK, T and SR. All of them act on the clock's
 * falling edge (1 to 0), start at Q = 0 and Q̅ = 1, and have Q̅ under Q on the right edge.
 */
class FlipFlopsTest {

    private static final int DATA_CLOCK = 2;
    private static final int DATA_OUTPUT = 1;
    private static final int DATA_NOT_OUTPUT = 3;
    /** JK and SR: the two inputs, the clock, Q and Q̅. */
    private static final int FIRST_INPUT = 0;
    private static final int SECOND_INPUT = 1;
    private static final int TWO_INPUT_CLOCK = 2;
    private static final int TWO_INPUT_OUTPUT = 3;
    private static final int TWO_INPUT_NOT_OUTPUT = 4;
    /** T: T, the clock, Q and Q̅. */
    private static final int TOGGLE = 0;
    private static final int TOGGLE_CLOCK = 1;
    private static final int TOGGLE_OUTPUT = 2;
    private static final int TOGGLE_NOT_OUTPUT = 3;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private static GateHarness fallingEdge(GateHarness harness, int clockPin) {
        return harness.set(clockPin, Value.ONE).advance(2).set(clockPin, Value.ZERO).advance(2);
    }

    private static int pinNamed(ComponentType type, String name) {
        return type.pins().stream().map(PinSpec::name).toList().indexOf(name);
    }

    private static Value bit(int value) {
        return value == 1 ? Value.ONE : Value.ZERO;
    }

    @Test
    void theDFlipFlopKeepsItsPinsAndAppendsNotQUnderQ() {
        ComponentType flipFlop = registry.require(StandardComponentTypes.FLIP_FLOP_DATA);
        assertThat(flipFlop.pins()).extracting(PinSpec::name).containsExactly("d", "q", "clock", "nQ");
        assertThat(flipFlop.pins()).extracting(PinSpec::position).containsExactly(new GridPoint(0, 2),
                new GridPoint(4, 2), new GridPoint(2, 4), new GridPoint(4, 3));
        assertThat(flipFlop.pin(DATA_NOT_OUTPUT).direction()).isEqualTo(PinDirection.OUTPUT);
    }

    @Test
    void anOldDFlipFlopFileOpensWithItsPinsAndProbesWhereTheyWere() throws Exception {
        String formatFour = """
                {"format": "vsim2", "formatVersion": 4, "kind": "circuit", "name": "old",
                 "components": [{"id": "c1", "type": "flip-flop-data", "x": 10, "y": 10, "rotation": 0, "label": "",
                                 "parameters": {"delay": 1}}],
                 "wires": [{"id": "w2", "x1": 14, "y1": 12, "x2": 18, "y2": 12}], "labels": [],
                 "probes": [{"id": "p1", "component": "c1", "pin": 2}]}
                """;

        CircuitDocument document = new CircuitFormat(registry).read(formatFour);

        Component flipFlop = document.components().getFirst();
        ComponentType type = registry.typeOf(flipFlop);
        assertThat(type.pinLocation(flipFlop, DATA_OUTPUT)).isEqualTo(new GridPoint(14, 12));
        assertThat(type.pinLocation(flipFlop, DATA_CLOCK)).isEqualTo(new GridPoint(12, 14));
        assertThat(document.probes().getFirst().target())
                .isEqualTo(new ProbeTarget.PinTarget(flipFlop.id(), DATA_CLOCK));
        assertThat(document.wires()).as("Q̅ is new and nothing touches it: no wire added").hasSize(1);
    }

    @Test
    void theDFlipFlopDrivesNotQAsTheComplementOfQ() {
        GateHarness harness = new GateHarness(StandardComponentTypes.FLIP_FLOP_DATA).set(0, Value.ONE)
                .set(DATA_CLOCK, Value.ZERO).advance(2);
        assertThat(harness.output(DATA_OUTPUT)).isEqualTo(Value.ZERO);
        assertThat(harness.output(DATA_NOT_OUTPUT)).isEqualTo(Value.ONE);

        fallingEdge(harness, DATA_CLOCK);

        assertThat(harness.output(DATA_OUTPUT)).isEqualTo(Value.ONE);
        assertThat(harness.output(DATA_NOT_OUTPUT)).isEqualTo(Value.ZERO);
    }

    @Test
    void theNewFlipFlopsAreSequentialPartsWithTheShapeOfTheDFlipFlop() {
        assertThat(registry.require(SequentialTypes.FLIP_FLOP_JK).pins()).extracting(PinSpec::name)
                .containsExactly("j", "k", "clock", "q", "nQ");
        assertThat(registry.require(SequentialTypes.FLIP_FLOP_SR).pins()).extracting(PinSpec::name)
                .containsExactly("s", "r", "clock", "q", "nQ");
        assertThat(registry.require(SequentialTypes.FLIP_FLOP_T).pins()).extracting(PinSpec::name)
                .containsExactly("t", "clock", "q", "nQ");
        assertThat(registry.require(SequentialTypes.FLIP_FLOP_JK).pins()).extracting(PinSpec::position)
                .containsExactly(new GridPoint(0, 1), new GridPoint(0, 3), new GridPoint(2, 4),
                        new GridPoint(4, 1), new GridPoint(4, 3));
        assertThat(registry.require(SequentialTypes.FLIP_FLOP_T).pins()).extracting(PinSpec::position)
                .containsExactly(new GridPoint(0, 2), new GridPoint(2, 4), new GridPoint(4, 2), new GridPoint(4, 3));
        for (String typeId : SequentialTypes.FLIP_FLOPS) {
            ComponentType type = registry.require(typeId);
            assertThat(type.category()).as(typeId).isEqualTo(ComponentCategory.SEQUENTIAL);
            assertThat(type.size()).as(typeId).isEqualTo(registry.require(StandardComponentTypes.FLIP_FLOP_DATA).size());
            assertThat(type.parameters()).extracting(parameter -> parameter.name()).containsExactly("delay");
        }
    }

    @Test
    void everyFlipFlopStartsAtQZeroAndNotQOne() {
        for (String typeId : SequentialTypes.FLIP_FLOPS) {
            ComponentType type = registry.require(typeId);
            GateHarness harness = new GateHarness(typeId).advance(2);
            assertThat(harness.output(pinNamed(type, "q"))).as(typeId + " Q").isEqualTo(Value.ZERO);
            assertThat(harness.output(pinNamed(type, "nQ"))).as(typeId + " Q̅").isEqualTo(Value.ONE);
        }
    }

    @ParameterizedTest(name = "Q = {0}, J = {1}, K = {2} → Q = {3}")
    @CsvSource({
            "0,0,0,0", "1,0,0,1",
            "0,0,1,0", "1,0,1,0",
            "0,1,0,1", "1,1,0,1",
            "0,1,1,1", "1,1,1,0"})
    void theJkFlipFlopHoldsResetsSetsAndToggles(int before, int jay, int kay, int after) {
        GateHarness harness = jkAt(before);

        harness.set(FIRST_INPUT, bit(jay)).set(SECOND_INPUT, bit(kay)).advance(2);
        assertThat(harness.output(TWO_INPUT_OUTPUT)).as("nothing changes before the edge").isEqualTo(bit(before));
        fallingEdge(harness, TWO_INPUT_CLOCK);

        assertThat(harness.output(TWO_INPUT_OUTPUT)).isEqualTo(bit(after));
        assertThat(harness.output(TWO_INPUT_NOT_OUTPUT)).isEqualTo(bit(1 - after));
    }

    private static GateHarness jkAt(int value) {
        GateHarness harness = new GateHarness(SequentialTypes.FLIP_FLOP_JK).set(TWO_INPUT_CLOCK, Value.ZERO)
                .set(FIRST_INPUT, bit(value)).set(SECOND_INPUT, bit(1 - value)).advance(2);
        return fallingEdge(harness, TWO_INPUT_CLOCK);
    }

    @Test
    void theJkFlipFlopActsOnlyOnTheFallingEdge() {
        GateHarness harness = new GateHarness(SequentialTypes.FLIP_FLOP_JK).set(FIRST_INPUT, Value.ONE)
                .set(SECOND_INPUT, Value.ZERO).set(TWO_INPUT_CLOCK, Value.ZERO).advance(2);

        harness.set(TWO_INPUT_CLOCK, Value.ONE).advance(2);

        assertThat(harness.output(TWO_INPUT_OUTPUT)).as("a rising edge does nothing").isEqualTo(Value.ZERO);
        harness.set(TWO_INPUT_CLOCK, Value.ZERO).advance(2);
        assertThat(harness.output(TWO_INPUT_OUTPUT)).isEqualTo(Value.ONE);
    }

    @Test
    void theTFlipFlopTogglesWithTOneAndHoldsWithTZero() {
        GateHarness harness = new GateHarness(SequentialTypes.FLIP_FLOP_T).set(TOGGLE, Value.ONE)
                .set(TOGGLE_CLOCK, Value.ZERO).advance(2);

        fallingEdge(harness, TOGGLE_CLOCK);
        assertThat(harness.output(TOGGLE_OUTPUT)).isEqualTo(Value.ONE);
        assertThat(harness.output(TOGGLE_NOT_OUTPUT)).isEqualTo(Value.ZERO);
        fallingEdge(harness, TOGGLE_CLOCK);
        assertThat(harness.output(TOGGLE_OUTPUT)).isEqualTo(Value.ZERO);

        harness.set(TOGGLE, Value.ZERO);
        fallingEdge(harness, TOGGLE_CLOCK);
        assertThat(harness.output(TOGGLE_OUTPUT)).isEqualTo(Value.ZERO);
        assertThat(harness.output(TOGGLE_NOT_OUTPUT)).isEqualTo(Value.ONE);
    }

    @Test
    void theSrFlipFlopSetsResetsAndHolds() {
        GateHarness harness = new GateHarness(SequentialTypes.FLIP_FLOP_SR).set(TWO_INPUT_CLOCK, Value.ZERO)
                .set(FIRST_INPUT, Value.ONE).set(SECOND_INPUT, Value.ZERO).advance(2);

        fallingEdge(harness, TWO_INPUT_CLOCK);
        assertThat(harness.output(TWO_INPUT_OUTPUT)).isEqualTo(Value.ONE);
        harness.set(FIRST_INPUT, Value.ZERO);
        fallingEdge(harness, TWO_INPUT_CLOCK);
        assertThat(harness.output(TWO_INPUT_OUTPUT)).as("00 holds").isEqualTo(Value.ONE);
        harness.set(SECOND_INPUT, Value.ONE);
        fallingEdge(harness, TWO_INPUT_CLOCK);
        assertThat(harness.output(TWO_INPUT_OUTPUT)).isEqualTo(Value.ZERO);
        assertThat(harness.output(TWO_INPUT_NOT_OUTPUT)).isEqualTo(Value.ONE);
    }

    @Test
    void theSrForbiddenStateShowsBothOutputsAtZeroUntilAValidCombination() {
        GateHarness harness = new GateHarness(SequentialTypes.FLIP_FLOP_SR).set(TWO_INPUT_CLOCK, Value.ZERO)
                .set(FIRST_INPUT, Value.ONE).set(SECOND_INPUT, Value.ONE).advance(2);

        fallingEdge(harness, TWO_INPUT_CLOCK);
        assertThat(harness.output(TWO_INPUT_OUTPUT)).isEqualTo(Value.ZERO);
        assertThat(harness.output(TWO_INPUT_NOT_OUTPUT)).isEqualTo(Value.ZERO);

        harness.set(FIRST_INPUT, Value.ZERO).set(SECOND_INPUT, Value.ZERO);
        fallingEdge(harness, TWO_INPUT_CLOCK);
        assertThat(harness.output(TWO_INPUT_NOT_OUTPUT)).as("00 holds the forbidden state").isEqualTo(Value.ZERO);

        harness.set(FIRST_INPUT, Value.ONE);
        fallingEdge(harness, TWO_INPUT_CLOCK);
        assertThat(harness.output(TWO_INPUT_OUTPUT)).isEqualTo(Value.ONE);
        assertThat(harness.output(TWO_INPUT_NOT_OUTPUT)).isEqualTo(Value.ZERO);
    }

    @Test
    void anUndefinedInputAtTheEdgeLeavesBothOutputsInZ() {
        GateHarness harness = new GateHarness(SequentialTypes.FLIP_FLOP_JK).set(TWO_INPUT_CLOCK, Value.ZERO)
                .set(FIRST_INPUT, Value.ONE).advance(2);

        fallingEdge(harness, TWO_INPUT_CLOCK);

        assertThat(harness.output(TWO_INPUT_OUTPUT)).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(harness.output(TWO_INPUT_NOT_OUTPUT)).isEqualTo(Value.HIGH_IMPEDANCE);
    }
}
