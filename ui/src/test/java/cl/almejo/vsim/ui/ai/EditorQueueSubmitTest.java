package cl.almejo.vsim.ui.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditorQueueSubmitTest {

    private final List<Runnable> turns = new ArrayList<>();

    private void runTurns() {
        List.copyOf(turns).forEach(Runnable::run);
        turns.clear();
    }

    @Test
    void theWorkRunsOnItsTurnAndCompletesTheFuture() {
        EditorQueue queue = new EditorQueue();
        CompletableFuture<String> result = queue.submit(turns::add, () -> "done");
        assertThat(result).isNotDone();
        runTurns();
        assertThat(result.join()).isEqualTo("done");
    }

    @Test
    void aFailureCompletesTheFutureExceptionally() {
        EditorQueue queue = new EditorQueue();
        CompletableFuture<String> result = queue.submit(turns::add, () -> {
            throw new IllegalStateException("boom");
        });
        runTurns();
        assertThatThrownBy(result::join).isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusedWorkNeverRuns() {
        EditorQueue queue = new EditorQueue();
        boolean[] ran = {false};
        CompletableFuture<String> result = queue.submit(turns::add, () -> {
            ran[0] = true;
            return "late";
        });
        queue.refuse();
        runTurns();
        assertThat(ran[0]).isFalse();
        assertThat(result).isCompletedExceptionally();
    }

    @Test
    void theAsyncVariantLetsTheBodyCompleteLater() {
        EditorQueue queue = new EditorQueue();
        CompletableFuture<String> later = new CompletableFuture<>();
        CompletableFuture<String> result = queue.submitAsync(turns::add,
                future -> later.thenAccept(future::complete));
        runTurns();
        assertThat(result).isNotDone();
        later.complete("answer");
        assertThat(result.join()).isEqualTo("answer");
    }
}
