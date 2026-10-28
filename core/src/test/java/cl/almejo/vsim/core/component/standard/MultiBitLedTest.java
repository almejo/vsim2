package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
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

class MultiBitLedTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentType ledType = registry.require(StandardComponentTypes.LED);
    private final ComponentType switchType = registry.require(StandardComponentTypes.SWITCH);
    private final SimulationEngine engine = new SimulationEngine();

    @Test
    void theDefaultsAreOneBitRedWithTheInputOnTheLeft() {
        assertThat(ledType.resolveParameters(Map.of()))
                .isEqualTo(Map.of("width", 1, "side", "left", "color", "red"));
    }

    @Test
    void theSideIsAHiddenParameter() {
        assertThat(ledType.parameters().stream().filter(parameter -> parameter.name().equals("side"))
                .allMatch(parameter -> parameter.hidden())).isTrue();
    }

    @Test
    void aOneBitLedKeepsTodaysShapeWhateverItsSide() {
        for (String side : MultiBitLayout.SIDES) {
            ComponentType oneBit = ledType.specializedFor(Map.of("side", side));
            assertThat(oneBit.size()).isEqualTo(new GridSize(2, 3));
            assertThat(oneBit.pin(0).position()).isEqualTo(new GridPoint(1, 3));
            assertThat(oneBit.pin(0).width()).isEqualTo(1);
            assertThat(oneBit.pin(0).direction()).isEqualTo(PinDirection.INPUT);
        }
    }

    @Test
    void aMultiBitLedHasOneBusInputOnTheLeftByDefault() {
        ComponentType led = ledType.specializedFor(Map.of("width", 8));

        assertThat(led.pinCount()).isEqualTo(1);
        assertThat(led.pin(0).width()).isEqualTo(8);
        assertThat(led.pin(0).direction()).isEqualTo(PinDirection.INPUT);
        assertThat(led.pin(0).position()).isEqualTo(MultiBitLayout.pinFor(8, "left"));
    }

    @Test
    void theLedAndTheSwitchOfTheSameWidthAndSideHaveTheSameBox() {
        for (int width = 2; width <= 32; width++) {
            for (String side : MultiBitLayout.SIDES) {
                Map<String, Object> parameters = Map.of("width", width, "side", side);
                ComponentType led = ledType.specializedFor(parameters);
                ComponentType lever = switchType.specializedFor(parameters);

                assertThat(led.size()).as("%d bits, %s", width, side).isEqualTo(lever.size());
                assertThat(led.pin(0).position()).as("%d bits, %s", width, side).isEqualTo(lever.pin(0).position());
            }
        }
    }

    @Test
    void theLedShowsTheWholeBusAsItsValue() {
        engine.update(new FlatCircuit(List.of(
                new FlatComponent("s", switchType.specializedFor(Map.of("width", 8)),
                        Map.of("width", 8, "value", "0xA5")),
                new FlatComponent("l", ledType.specializedFor(Map.of("width", 8)), Map.of("width", 8))),
                List.of(new FlatNet("n", List.of(new FlatPinRef("s", 0), new FlatPinRef("l", 0)), 8, false))));

        assertThat(engine.snapshot().componentState("l")).isEqualTo(new VisibleState.LedState(Value.of(8, 0xA5)));
    }

    @Test
    void anUnconnectedMultiBitLedHoldsAllZ() {
        engine.update(new FlatCircuit(List.of(
                new FlatComponent("l", ledType.specializedFor(Map.of("width", 4)), Map.of("width", 4))),
                List.of(new FlatNet("n", List.of(new FlatPinRef("l", 0)), 4, false))));

        assertThat(engine.snapshot().componentState("l"))
                .isEqualTo(new VisibleState.LedState(Value.highImpedance(4)));
    }

    @Test
    void aOneBitLedStillReadsItsInput() {
        engine.update(new FlatCircuit(List.of(
                new FlatComponent("s", switchType, Map.of("value", "1")),
                new FlatComponent("l", ledType, Map.of("color", "green"))),
                List.of(new FlatNet("n", List.of(new FlatPinRef("s", 0), new FlatPinRef("l", 0)), 1, false))));

        assertThat(engine.snapshot().componentState("l")).isEqualTo(new VisibleState.LedState(Value.ONE));
    }
}
