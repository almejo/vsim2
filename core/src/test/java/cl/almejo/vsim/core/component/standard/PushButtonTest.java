package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.TestParts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The push button («Pulsador») as a type: a one-bit input, a test part like the Switch. */
class PushButtonTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void itIsAOneBitInputRightAfterTheSwitchWithItsOutputOnTheRightAndNoParameters() {
        ComponentType button = registry.require(StandardComponentTypes.PUSH_BUTTON);

        assertThat(button.category()).isEqualTo(ComponentCategory.INPUT_OUTPUT);
        assertThat(button.parameters()).isEmpty();
        assertThat(button.pins()).hasSize(1);
        assertThat(button.pin(0).direction()).isEqualTo(PinDirection.OUTPUT);
        assertThat(button.pin(0).width()).isEqualTo(1);
        assertThat(button.pin(0).position()).isEqualTo(new GridPoint(4, 1));
        List<String> inputOutput = registry.all().stream()
                .filter(type -> type.category() == ComponentCategory.INPUT_OUTPUT).map(ComponentType::id).toList();
        assertThat(inputOutput.indexOf(StandardComponentTypes.PUSH_BUTTON))
                .isEqualTo(inputOutput.indexOf(StandardComponentTypes.SWITCH) + 1);
    }

    @Test
    void itIsATestPartThatBecomesAnInputLikeTheSwitch() {
        assertThat(TestParts.kindOf(StandardComponentTypes.PUSH_BUTTON)).contains(TestParts.Kind.SWITCH);
        assertThat(StandardComponentTypes.isSwitchOrPushButton(StandardComponentTypes.PUSH_BUTTON)).isTrue();
        assertThat(StandardComponentTypes.isSwitchOrPushButton(StandardComponentTypes.SWITCH)).isTrue();
        assertThat(StandardComponentTypes.isSwitchOrPushButton(StandardComponentTypes.INPUT_PIN)).isFalse();
    }

    @Test
    void aRestartStartsItReleasedEvenIfTheAiOrATruthTableRowLeftItAtOne() {
        ComponentType button = registry.require(StandardComponentTypes.PUSH_BUTTON);
        ComponentType input = registry.require(StandardComponentTypes.SWITCH);
        FlatCircuit circuit = new FlatCircuit(
                List.of(new FlatComponent("button", button, Map.of()), new FlatComponent("switch", input, Map.of())),
                List.of(new FlatNet("pressed", List.of(new FlatPinRef("button", 0)), 1, false),
                        new FlatNet("on", List.of(new FlatPinRef("switch", 0)), 1, false)));
        SimulationEngine engine = new SimulationEngine();
        engine.update(circuit);
        engine.runUntil(engine.now());
        engine.setInput("button", Value.ONE);
        engine.setInput("switch", Value.ONE);
        assertThat(engine.netValue("pressed")).isEqualTo(Value.ONE);

        engine.restart(circuit, Map.of());

        assertThat(engine.netValue("pressed")).isEqualTo(Value.ZERO);
        assertThat(engine.netValue("on")).as("a switch still keeps its value").isEqualTo(Value.ONE);
    }
}
