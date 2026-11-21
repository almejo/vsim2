package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterProfiles;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A request whose work throws on the simulation thread (SIM-6 of the 2026-11-20 bug hunt): its future completes with
 * the failure, so whoever waits on it (the AI, «Volcar») gets an answer instead of waiting forever, and the
 * simulation still shows the failure as before.
 */
class SimulationThreadRequestFailureTest {

    private static final String BROKEN_KEYBOARD = "test-broken-keyboard";

    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), () -> 0L);

    /** A part that takes typing but cannot tell its queue: its visible state fails. */
    private static ComponentType brokenKeyboard() {
        return new ComponentType(BROKEN_KEYBOARD, ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2),
                List.of(PinSpec.output("out", 2, 1)), List.of(), parameters -> new Behavior() {
                    @Override
                    public void inputChanged(BehaviorContext context, int pinIndex) {
                    }

                    @Override
                    public boolean acceptsTyping() {
                        return true;
                    }

                    @Override
                    public VisibleState visibleState() {
                        throw new IllegalStateException("broken queue");
                    }
                });
    }

    private static void assertFailedWith(CompletableFuture<?> future, Class<? extends Throwable> failure) {
        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::join).isInstanceOf(CompletionException.class).hasCauseInstanceOf(failure);
    }

    @Test
    void aTerminalReadingThatThrowsCompletesItsFutureWithTheFailure() {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        Map<String, Object> parameters = ParameterProfiles.apply(registry.require(StandardComponentTypes.TERMINAL),
                Map.of(), TerminalTypes.PROFILE_VT);
        ComponentType terminal = registry.require(StandardComponentTypes.TERMINAL).specializedFor(parameters);
        thread.update(new FlatCircuit(List.of(new FlatComponent("tty", terminal, parameters)), List.of()));

        CompletableFuture<Optional<TerminalReading>> reading = thread.requestTerminalReading("tty", -1, 0);
        thread.tick();

        assertFailedWith(reading, IllegalArgumentException.class);
        assertThat(thread.latestSnapshot().failure()).as("the failure is still shown").isPresent();
    }

    @Test
    void aTypingQueueSizeThatThrowsCompletesItsFutureWithTheFailure() {
        ComponentType keyboard = brokenKeyboard();
        thread.update(new FlatCircuit(List.of(new FlatComponent("keys", keyboard, Map.of())), List.of()));

        CompletableFuture<OptionalInt> size = thread.requestTypingQueueSize("keys");
        thread.tick();

        assertFailedWith(size, IllegalStateException.class);
    }

    @Test
    void theWorkOfARequestThatThrowsCompletesItsFutureWithTheFailureAndRethrows() {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        IllegalStateException broken = new IllegalStateException("broken memory");

        assertThatThrownBy(() -> SimulationThread.answer(future, () -> {
            throw broken;
        })).isSameAs(broken);
        assertFailedWith(future, IllegalStateException.class);
    }
}
