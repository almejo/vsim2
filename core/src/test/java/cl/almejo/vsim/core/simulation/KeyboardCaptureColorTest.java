package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** «Color al escribir» is only a look: it never rebuilds the Teclado nor touches its queue. */
class KeyboardCaptureColorTest {

    private static final ComponentType TYPE = StandardComponentTypes.registry()
            .require(StandardComponentTypes.KEYBOARD);

    private static FlatCircuit circuit(Map<String, Object> parameters) {
        return new FlatCircuit(List.of(new FlatComponent("kb", TYPE.specializedFor(parameters), parameters)),
                List.of());
    }

    @Test
    void theParameterIsDisplayOnlyInTheAppearanceGroupWithAutomaticAsDefault() {
        ParameterSpec spec = TYPE.parameters().stream()
                .filter(parameter -> parameter.name().equals(KeyboardTypes.CAPTURE_COLOR)).findFirst().orElseThrow();

        assertThat(spec.displayOnly()).isTrue();
        assertThat(spec.group()).contains("appearance");
        assertThat(spec.defaultValue()).isEqualTo("automatic");
        assertThat(spec.choices()).containsExactly("automatic", "blue", "green", "purple", "orange", "pink", "teal",
                "yellow");
    }

    @Test
    void changingItKeepsTheQueue() {
        SimulationEngine engine = new SimulationEngine();
        engine.update(circuit(Map.of()));
        engine.typeBytes("kb", "hi".getBytes());

        engine.update(circuit(Map.of(KeyboardTypes.CAPTURE_COLOR, "pink")));

        VisibleState.KeyboardState state = (VisibleState.KeyboardState) engine.snapshot().componentState("kb");
        assertThat(state.queue()).containsExactly((int) 'h', (int) 'i');
    }
}
