package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** How full a Teclado's queue is, asked of the simulation thread (the AI's «vsim_type_text» answer). */
class SimulationThreadKeyboardTest {

    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), () -> 0L);

    private FlatCircuit circuit() {
        ComponentType keyboard = StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD);
        return new FlatCircuit(List.of(new FlatComponent("keys", keyboard, Map.of())), List.of());
    }

    @Test
    void theQueueSizeCountsTheBytesTyped() {
        thread.update(circuit());
        thread.tick();
        thread.typeCharacters("keys", List.of(new byte[]{'h'}, new byte[]{'i'}));

        CompletableFuture<OptionalInt> size = thread.requestTypingQueueSize("keys");
        assertThat(size).isNotDone();
        thread.tick();

        assertThat(size.join()).hasValue(2);
    }

    @Test
    void aPartThatTakesNoTypingOrAClosedThreadHasNoQueue() {
        thread.update(new FlatCircuit(List.of(new FlatComponent("other", StandardComponentTypes.registry()
                .require(StandardComponentTypes.CONSTANT), Map.of())), List.of()));
        thread.tick();

        CompletableFuture<OptionalInt> size = thread.requestTypingQueueSize("other");
        thread.tick();
        assertThat(size.join()).isEmpty();

        thread.close();
        assertThat(thread.requestTypingQueueSize("other").join()).isEmpty();
    }
}
