package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link SimulationEngine#setInput} (M6.1-C): what the truth-table evaluator uses to try each input combination. */
class SimulationEngineInputTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final SimulationEngine engine = new SimulationEngine();

    private FlatComponent component(String id, String typeId, Map<String, Object> parameters) {
        ComponentType type = registry.require(typeId).specializedFor(parameters);
        return new FlatComponent(id, type, parameters);
    }

    /** {@code source} drives net "n", read by a NOT gate "gate" whose output is net "out". */
    private void simulate(FlatComponent source, int width) {
        engine.update(new FlatCircuit(List.of(source, component("gate", StandardComponentTypes.BUFFER, Map.of())),
                List.of(new FlatNet("n", List.of(new FlatPinRef(source.id(), 0)), width, false),
                        new FlatNet("out", List.of(new FlatPinRef("gate", 1)), 1, false))));
    }

    @Test
    void aSwitchDrivesTheNewValueAtOnce() {
        simulate(component("s", StandardComponentTypes.SWITCH, Map.of("value", "1")), 1);
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ONE);

        engine.setInput("s", Value.ZERO);

        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.ZERO);
        assertThat(engine.now()).isZero();
        assertThat(engine.snapshot().componentState("s")).isEqualTo(new VisibleState.SwitchState(Value.ZERO));
    }

    @Test
    void anInputPinTakesAnyValueOfItsWidth() {
        Map<String, Object> twoBits = Map.of("value", "0", InterfacePins.WIDTH, 2);
        simulate(component("pin", InterfacePins.INPUT_PIN, twoBits), 2);

        engine.setInput("pin", Value.of(2, 2));

        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.of(2, 2));
        assertThatThrownBy(() -> engine.setInput("pin", Value.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.of(2, 2));
    }

    @Test
    void anUnknownIdOrAComponentThatIsNotAnInputIsRefused() {
        simulate(component("s", StandardComponentTypes.SWITCH, Map.of()), 1);

        assertThatThrownBy(() -> engine.setInput("missing", Value.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> engine.setInput("gate", Value.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not an input");
    }
}
