package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUS_JOINER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUS_SPLITTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.GROUPS;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Separador de bus» and «Unión de bus» with groups of bits: their pins and what they copy, and that the parts without
 * groups keep exactly today's pins.
 */
class GroupedBusTest {

    private static final String RISC_V_FIELDS = "0-6|7-11|12-14|15-19|20-24|25-31";
    private static final String SW_IMMEDIATE = "7-11,25-31";

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void withoutGroupsBothPartsHaveTodaysPinsForEveryWidth() {
        for (int width = 2; width <= 32; width++) {
            for (boolean joining : List.of(false, true)) {
                ComponentType type = registry.require(joining ? BUS_JOINER : BUS_SPLITTER)
                        .specializedFor(Map.of(WIDTH, width));
                assertThat(type.size()).as("width %d", width).isEqualTo(new GridSize(2, width));
                assertThat(type.pins()).as("width %d", width).containsExactlyElementsOf(oldPins(width, joining));
            }
        }
    }

    @Test
    void theGroupsParameterIsHiddenAndEmptyByDefault() {
        ComponentType splitter = registry.require(BUS_SPLITTER);

        assertThat(splitter.parameterNamed(GROUPS).orElseThrow().hidden()).isTrue();
        assertThat(splitter.resolveParameters(Map.of(WIDTH, 8))).containsEntry(GROUPS, "");
        assertThat(registry.require(BUS_JOINER).resolveParameters(Map.of())).containsEntry(GROUPS, "");
    }

    @Test
    void theGroupsAreStoredCanonically() {
        Map<String, Object> resolved = registry.require(BUS_SPLITTER)
                .resolveParameters(Map.of(WIDTH, 8, GROUPS, " 4-7 | 3,0-2 "));

        assertThat(resolved).containsEntry(GROUPS, "4-7|0-3");
    }

    @Test
    void invalidGroupsAreRefusedWithTheirProblem() {
        ComponentType splitter = registry.require(BUS_SPLITTER);

        assertThat(splitter.check(Map.of(WIDTH, 8, GROUPS, "0-3|4-8")))
                .contains(new ParameterProblem(GROUPS, "groups.bitOutOfRange", List.of(8, 8)));
        assertThat(registry.require(BUS_JOINER).check(Map.of(WIDTH, 4, GROUPS, "0-1|1-3")))
                .contains(new ParameterProblem(GROUPS, "groups.bitInTwoPins", List.of(1)));
        assertThatThrownBy(() -> splitter.specializedFor(Map.of(WIDTH, 4, GROUPS, "0-7")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aGroupedSplitterHasOnePinPerGroupNamedAfterItsBits() {
        ComponentType splitter = registry.require(BUS_SPLITTER)
                .specializedFor(Map.of(WIDTH, 32, GROUPS, SW_IMMEDIATE + "|5|0-3"));

        // pins two units apart; «7-11,25-31» (10 characters) asks for a lead of 6 units, plus the unit to the spine
        assertThat(splitter.size()).isEqualTo(new GridSize(7, 5));
        assertThat(splitter.pins()).containsExactly(
                new PinSpec("bus", GridPoint.ORIGIN, PinDirection.INPUT, 32),
                new PinSpec("b" + SW_IMMEDIATE, new GridPoint(7, 1), PinDirection.OUTPUT, 12),
                new PinSpec("b5", new GridPoint(7, 3), PinDirection.OUTPUT, 1),
                new PinSpec("b0-3", new GridPoint(7, 5), PinDirection.OUTPUT, 4));
    }

    @Test
    void theLeadOfAGroupedPartFitsItsLongestLabelAtTwoCharactersAUnit() {
        ComponentType splitter = registry.require(BUS_SPLITTER);

        assertThat(splitter.specializedFor(Map.of(WIDTH, 8, GROUPS, "0-3|4-7")).size())
                .isEqualTo(new GridSize(3, 3));
        assertThat(splitter.specializedFor(Map.of(WIDTH, 16, GROUPS, "0-7|8-15")).size())
                .isEqualTo(new GridSize(4, 3));
        assertThat(splitter.specializedFor(Map.of(WIDTH, 32, GROUPS, RISC_V_FIELDS)).size())
                .isEqualTo(new GridSize(4, 11));
        assertThat(splitter.specializedFor(Map.of(WIDTH, 8, GROUPS, "0-7")).pins())
                .containsExactly(new PinSpec("bus", GridPoint.ORIGIN, PinDirection.INPUT, 8),
                        new PinSpec("b0-7", new GridPoint(3, 1), PinDirection.OUTPUT, 8));
    }

    @Test
    void singleBitPinsInAnotherOrderKeepTodaysSpacingAndWidth() {
        ComponentType splitter = registry.require(BUS_SPLITTER).specializedFor(Map.of(WIDTH, 32, GROUPS, "31|0"));

        assertThat(splitter.size()).isEqualTo(new GridSize(2, 2));
        assertThat(splitter.pins()).containsExactly(
                new PinSpec("bus", GridPoint.ORIGIN, PinDirection.INPUT, 32),
                new PinSpec("b31", new GridPoint(2, 1), PinDirection.OUTPUT, 1),
                new PinSpec("b0", new GridPoint(2, 2), PinDirection.OUTPUT, 1));
    }

    @Test
    void twoGroupingsOfTheSameWidthGiveTheirOwnPinsEachTime() {
        ComponentType splitter = registry.require(BUS_SPLITTER);

        for (int round = 0; round < 2; round++) {
            assertThat(splitter.specializedFor(Map.of(WIDTH, 8, GROUPS, "0-3|4-7")).pinCount()).isEqualTo(3);
            assertThat(splitter.specializedFor(Map.of(WIDTH, 8, GROUPS, "0-7")).pinCount()).isEqualTo(2);
            assertThat(splitter.specializedFor(Map.of(WIDTH, 8)).pinCount()).isEqualTo(9);
        }
    }

    @Test
    void aGroupedJoinerIsTheMirror() {
        ComponentType joiner = registry.require(BUS_JOINER).specializedFor(Map.of(WIDTH, 8, GROUPS, "0-3|4-7"));

        assertThat(joiner.size()).isEqualTo(new GridSize(3, 3));
        assertThat(joiner.pins()).containsExactly(
                new PinSpec("bus", new GridPoint(3, 0), PinDirection.OUTPUT, 8),
                new PinSpec("b0-3", new GridPoint(0, 1), PinDirection.INPUT, 4),
                new PinSpec("b4-7", new GridPoint(0, 3), PinDirection.INPUT, 4));
    }

    @Test
    void theSplitterCutsAByteInNibbles() {
        SimulationSnapshot snapshot = split(8, "0-3|4-7", "0xA7", false);

        assertThat(snapshot.netValue("pin0")).isEqualTo(Value.of(4, 0x7));
        assertThat(snapshot.netValue("pin1")).isEqualTo(Value.of(4, 0xA));
    }

    @Test
    void theSplitterGivesTheFieldsOfAnRTypeInstruction() {
        SimulationSnapshot snapshot = split(32, RISC_V_FIELDS, "0x007302B3", false);

        assertThat(snapshot.netValue("pin0")).isEqualTo(Value.of(7, 0x33));
        assertThat(snapshot.netValue("pin1")).isEqualTo(Value.of(5, 5));
        assertThat(snapshot.netValue("pin2")).isEqualTo(Value.of(3, 0));
        assertThat(snapshot.netValue("pin3")).isEqualTo(Value.of(5, 6));
        assertThat(snapshot.netValue("pin4")).isEqualTo(Value.of(5, 7));
        assertThat(snapshot.netValue("pin5")).isEqualTo(Value.of(7, 0));
    }

    @Test
    void theSplitterJoinsTheTwoHalvesOfTheSwImmediateLowestBitsFirst() {
        SimulationSnapshot snapshot = split(32, SW_IMMEDIATE, "0x00512623", false);

        assertThat(snapshot.netValue("pin0")).isEqualTo(Value.of(12, 12));
    }

    @Test
    void theHighHalfOfTheSwImmediateLandsAboveTheLowHalf() {
        SimulationSnapshot snapshot = split(32, SW_IMMEDIATE, "0xFE000000", false);

        assertThat(snapshot.netValue("pin0")).isEqualTo(Value.of(12, 0b1111111_00000));
    }

    @Test
    void aSplitterOnAMismatchedBusGivesZ() {
        SimulationSnapshot snapshot = split(8, "0-3|4-7", "0xA7", true);

        assertThat(snapshot.netValue("pin0")).isEqualTo(Value.highImpedance(4));
        assertThat(snapshot.netValue("pin1")).isEqualTo(Value.highImpedance(4));
    }

    @Test
    void theJoinerPlacesEachGroupAndLeavesUnusedBitsZ() {
        SimulationSnapshot snapshot = join(8, "0-3", List.of("0x5"));

        assertThat(snapshot.netValue("bus")).isEqualTo(new Value(8, 0x05, 0xF0, false));
    }

    @Test
    void theJoinerBuildsTheSwImmediateBackIntoItsBusBits() {
        SimulationSnapshot snapshot = join(32, SW_IMMEDIATE + "|0-6", List.of("0xFEC", "0x23"));

        assertThat(snapshot.netValue("bus")).isEqualTo(new Value(32, 0xFE000623L, 0x01FFF000L, false));
    }

    @Test
    void aJoinerKeepsAZBitOfAGroup() {
        SimulationSnapshot snapshot = join(4, "0-1|2-3", List.of("0x1", "Z"));

        assertThat(snapshot.netValue("bus")).isEqualTo(new Value(4, 0b0001, 0b1100, false));
    }

    /** The pins of a «Separador de bus» / «Unión de bus» before groups existed, written out independently. */
    private static List<PinSpec> oldPins(int width, boolean joining) {
        List<PinSpec> pins = new ArrayList<>();
        pins.add(new PinSpec("bus", new GridPoint(joining ? 2 : 0, 0),
                joining ? PinDirection.OUTPUT : PinDirection.INPUT, width));
        for (int bit = 0; bit < width; bit++) {
            pins.add(joining ? PinSpec.input("b" + bit, 0, bit + 1) : PinSpec.output("b" + bit, 2, bit + 1));
        }
        return pins;
    }

    /** A constant {@code value} → net "bus" → a grouped splitter → nets pin0..pinN. */
    private SimulationSnapshot split(int width, String groups, String value, boolean busMismatched) {
        Map<String, Object> parameters = Map.of(WIDTH, width, GROUPS, groups);
        ComponentType splitter = registry.require(BUS_SPLITTER).specializedFor(parameters);
        List<FlatComponent> components = new ArrayList<>(List.of(
                new FlatComponent("splitter", splitter, parameters), constant("source", width, value)));
        List<FlatNet> nets = new ArrayList<>(List.of(new FlatNet("bus",
                List.of(new FlatPinRef("source", 0), new FlatPinRef("splitter", 0)), width, busMismatched)));
        for (int pin = 1; pin < splitter.pinCount(); pin++) {
            nets.add(new FlatNet("pin" + (pin - 1), List.of(new FlatPinRef("splitter", pin)),
                    splitter.pin(pin).width(), false));
        }
        return run(components, nets);
    }

    /** One constant per group ({@code "Z"} drives nothing) → a grouped joiner → net "bus". */
    private SimulationSnapshot join(int width, String groups, List<String> values) {
        Map<String, Object> parameters = Map.of(WIDTH, width, GROUPS, groups);
        ComponentType joiner = registry.require(BUS_JOINER).specializedFor(parameters);
        List<FlatComponent> components = new ArrayList<>(List.of(new FlatComponent("joiner", joiner, parameters)));
        List<FlatNet> nets = new ArrayList<>(List.of(
                new FlatNet("bus", List.of(new FlatPinRef("joiner", 0)), width, false)));
        for (int pin = 1; pin < joiner.pinCount(); pin++) {
            String value = values.get(pin - 1);
            List<FlatPinRef> pins = new ArrayList<>(List.of(new FlatPinRef("joiner", pin)));
            if (!"Z".equals(value)) {
                components.add(constant("source" + pin, joiner.pin(pin).width(), value));
                pins.add(new FlatPinRef("source" + pin, 0));
            }
            nets.add(new FlatNet("pin" + pin, pins, joiner.pin(pin).width(), false));
        }
        return run(components, nets);
    }

    private FlatComponent constant(String id, int width, String value) {
        Map<String, Object> parameters = Map.of(WIDTH, width, "value", value);
        return new FlatComponent(id, registry.require(StandardComponentTypes.CONSTANT).specializedFor(parameters),
                parameters);
    }

    private static SimulationSnapshot run(List<FlatComponent> components, List<FlatNet> nets) {
        SimulationEngine engine = new SimulationEngine();
        engine.update(new FlatCircuit(components, nets));
        engine.advance(5);
        return engine.snapshot();
    }
}
