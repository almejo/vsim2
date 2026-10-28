package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultiBitSwitchTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentType switchType = registry.require(StandardComponentTypes.SWITCH);
    private final SimulationEngine engine = new SimulationEngine();

    private ComponentType specialized(Map<String, Object> parameters) {
        return switchType.specializedFor(parameters);
    }

    private void simulate(Map<String, Object> parameters) {
        ComponentType type = specialized(parameters);
        int width = (Integer) type.resolveParameters(parameters).get("width");
        engine.update(new FlatCircuit(List.of(new FlatComponent("s", type, parameters)),
                List.of(new FlatNet("n", List.of(new FlatPinRef("s", 0)), width, false))));
    }

    @Test
    void theDefaultsAreOneBitOnTheRightWithTheValueOne() {
        assertThat(switchType.resolveParameters(Map.of()))
                .isEqualTo(Map.of("width", 1, "side", "right", "value", "1"));
    }

    @Test
    void theSideIsAHiddenParameter() {
        assertThat(switchType.parameters().stream().filter(parameter -> parameter.name().equals("side"))
                .allMatch(parameter -> parameter.hidden())).isTrue();
    }

    @Test
    void aOneBitSwitchKeepsTodaysShapeWhateverItsSide() {
        for (String side : List.of("right", "bottom", "left", "top")) {
            ComponentType oneBit = specialized(Map.of("side", side));
            assertThat(oneBit.size()).isEqualTo(new GridSize(4, 2));
            assertThat(oneBit.pin(0).position()).isEqualTo(new GridPoint(4, 1));
            assertThat(oneBit.pin(0).width()).isEqualTo(1);
        }
    }

    @Test
    void aMultiBitSwitchHasOneBusPinOnItsSide() {
        ComponentType right = specialized(Map.of("width", 8));
        ComponentType top = specialized(Map.of("width", 8, "side", "top"));

        assertThat(right.pinCount()).isEqualTo(1);
        assertThat(right.pin(0).width()).isEqualTo(8);
        assertThat(right.pin(0).position()).isEqualTo(MultiBitLayout.pinFor(8, "right"));
        assertThat(right.size()).isEqualTo(new GridSize(12, 3));
        assertThat(top.pin(0).position()).isEqualTo(MultiBitLayout.pinFor(8, "top"));
    }

    @Test
    void theInitialValueIsANumberOfTheWidthAndDrivesTheBus() {
        simulate(Map.of("width", 8, "value", "0xA6"));

        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.of(8, 0xA6));
    }

    @Test
    void clickingABitTogglesOnlyThatBit() {
        simulate(Map.of("width", 4, "value", "0b1010"));

        engine.interactBit("s", 3);

        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.of(4, 0b0010));
        assertThat(engine.snapshot().componentState("s"))
                .isEqualTo(new VisibleState.SwitchState(Value.of(4, 0b0010)));

        engine.interactBit("s", 0);

        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.of(4, 0b0011));
    }

    @Test
    void clickingABitOutOfRangeChangesNothing() {
        simulate(Map.of("width", 4, "value", "5"));

        engine.interactBit("s", 4);

        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.of(4, 5));
    }

    @Test
    void theValueMustBeANumberThatFitsTheWidth() {
        assertThat(switchType.check(Map.of("width", 4, "value", "0xF"))).isEmpty();
        assertThat(switchType.check(Map.of("width", 4, "value", "0x1F")))
                .contains(new ParameterProblem("value", "constant.doesNotFit", List.of(4)));
        assertThat(switchType.check(Map.of("width", 4, "value", "abc")))
                .contains(new ParameterProblem("value", "constant.invalidValue", List.of()));
    }

    @Test
    void aMultiBitSwitchHasNoZ() {
        assertThat(switchType.check(Map.of("width", 4, "value", "Z")))
                .contains(new ParameterProblem("value", "switch.noZ", List.of()));
        assertThat(switchType.check(Map.of("width", 1, "value", "Z"))).isEmpty();
    }

    @Test
    void aOneBitSwitchStillReadsOldValuesAndSimulatesThem() {
        simulate(Map.of("value", "Z"));
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.HIGH_IMPEDANCE);

        simulate(Map.of("value", "0"));
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ZERO);

        simulate(Map.of("value", "1"));
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ONE);
        engine.interact("s");
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ZERO);
    }
}
