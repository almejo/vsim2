package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.terminal.TypingEdit;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keys typed on the canvas reach a part that takes typing through the simulation thread (spec §11): queued like a
 * click, applied at the current time, running or paused, and shown as a step while paused.
 */
class SimulationThreadTypingTest {

    private static final int DATA_WIDTH = 8;
    private static final int CAPACITY = 3;

    private final SimulationThread thread = new SimulationThread(new SimulationEngine(), () -> 0L);
    private final List<Byte> received = new ArrayList<>();
    private final List<TypingEdit> edits = new ArrayList<>();

    /** A part that keeps up to {@link #CAPACITY} bytes and drives the last one on its only pin. */
    private final class Typist implements Behavior {

        @Override
        public void inputChanged(BehaviorContext context, int pinIndex) {
        }

        @Override
        public boolean acceptsTyping() {
            return true;
        }

        @Override
        public int acceptBytes(BehaviorContext context, byte[] bytes) {
            int accepted = Math.min(bytes.length, CAPACITY - received.size());
            for (int index = 0; index < accepted; index++) {
                received.add(bytes[index]);
            }
            if (accepted > 0) {
                context.drive(0, Value.of(DATA_WIDTH, bytes[accepted - 1] & 0xFF), 0);
            }
            return accepted;
        }

        @Override
        public void editTyping(BehaviorContext context, TypingEdit edit) {
            edits.add(edit);
        }
    }

    private FlatCircuit circuit() {
        ComponentType typist = new ComponentType("typist", ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2),
                List.of(PinSpec.output("D", 2, 1, DATA_WIDTH)), List.of(), parameters -> new Typist());
        ComponentType constant = StandardComponentTypes.registry().require(StandardComponentTypes.CONSTANT);
        return new FlatCircuit(List.of(new FlatComponent("keys", typist, Map.of()),
                new FlatComponent("other", constant, Map.of())),
                List.of(new FlatNet("data", List.of(new FlatPinRef("keys", 0)), DATA_WIDTH, false),
                        new FlatNet("constant", List.of(new FlatPinRef("other", 0)), 1, false)));
    }

    @Test
    void typedBytesWaitForTheSimulationThreadAndReachThePartWhileRunning() {
        thread.update(circuit());
        thread.resume();
        thread.tick();

        CompletableFuture<Integer> accepted = thread.typeBytes("keys", new byte[]{'h', 'i'});
        assertThat(received).isEmpty();
        assertThat(accepted).isNotDone();
        thread.tick();

        assertThat(accepted).isCompletedWithValue(2);
        assertThat(received).containsExactly((byte) 'h', (byte) 'i');
        assertThat(thread.latestSnapshot().netValue("data")).isEqualTo(Value.of(DATA_WIDTH, 'i'));
    }

    @Test
    void typingWhilePausedIsAStepWithTheNetThatChanged() {
        thread.update(circuit());
        thread.tick();

        CompletableFuture<Integer> accepted = thread.typeBytes("keys", new byte[]{'a'});
        thread.tick();

        assertThat(accepted).isCompletedWithValue(1);
        assertThat(thread.latestSnapshot().netValue("data")).isEqualTo(Value.of(DATA_WIDTH, 'a'));
        assertThat(thread.latestStep()).isPresent();
        assertThat(thread.latestStep().orElseThrow().result().changedNets()).contains("data");
    }

    @Test
    void thePartSaysHowManyBytesItTook() {
        thread.update(circuit());
        thread.tick();

        CompletableFuture<Integer> accepted = thread.typeBytes("keys", new byte[]{'1', '2', '3', '4'});
        thread.tick();

        assertThat(accepted).isCompletedWithValue(CAPACITY);
    }

    @Test
    void charactersStopAtTheFirstOneThePartRefuses() {
        thread.update(circuit());
        thread.tick();

        CompletableFuture<Integer> accepted = thread.typeCharacters("keys",
                List.of(new byte[]{'a'}, new byte[]{(byte) 0xC3, (byte) 0xB1}, new byte[]{'b'}, new byte[]{'c'}));
        thread.tick();

        assertThat(accepted).isCompletedWithValue(2);
        assertThat(received).containsExactly((byte) 'a', (byte) 0xC3, (byte) 0xB1);
    }

    @Test
    void aPartThatDoesNotTakeTypingOrIsGoneTakesNothing() {
        thread.update(circuit());
        thread.tick();

        CompletableFuture<Integer> toAConstant = thread.typeBytes("other", new byte[]{'a'});
        CompletableFuture<Integer> toNobody = thread.typeBytes("gone", new byte[]{'a'});
        thread.tick();

        assertThat(toAConstant).isCompletedWithValue(0);
        assertThat(toNobody).isCompletedWithValue(0);
    }

    @Test
    void editsOfTheQueueReachThePart() {
        thread.update(circuit());
        thread.resume();
        thread.tick();

        thread.editTyping("keys", TypingEdit.CURSOR_LEFT);
        thread.editTyping("keys", TypingEdit.DELETE);
        thread.tick();

        assertThat(edits).containsExactly(TypingEdit.CURSOR_LEFT, TypingEdit.DELETE);
    }

    @Test
    void typingAfterTheThreadClosedTakesNothing() {
        thread.update(circuit());
        thread.tick();
        thread.close();

        assertThat(thread.typeBytes("keys", new byte[]{'a'})).isCompletedWithValue(0);
    }
}
