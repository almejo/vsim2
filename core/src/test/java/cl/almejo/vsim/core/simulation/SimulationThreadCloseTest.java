package cl.almejo.vsim.core.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The AI's budgeted requests never wait forever on a simulation that was closed (IA.1 review M5). */
class SimulationThreadCloseTest {

    @Test
    void closingFailsTheReceiptsOfTheRequestsStillQueued() {
        SimulationThread thread = new SimulationThread(new SimulationEngine());
        CompletableFuture<RequestReport> step = thread.stepWithin(10, 1_000);
        CompletableFuture<RequestReport> inputs = thread.setInputsWithin(List.of(), 1_000);
        CompletableFuture<RequestReport> report = thread.report();

        thread.close();

        for (CompletableFuture<RequestReport> future : List.of(step, inputs, report)) {
            assertThatThrownBy(future::join).isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(SimulationThread.ClosedException.class);
        }
    }

    @Test
    void aRequestAfterTheCloseIsFailedAtOnce() {
        SimulationThread thread = new SimulationThread(new SimulationEngine());
        thread.close();

        assertThat(thread.stepWithin(10, 1_000)).isCompletedExceptionally();
    }

    @Test
    void aReceiptAnsweredBeforeTheCloseStaysAnswered() throws Exception {
        SimulationThread thread = new SimulationThread(new SimulationEngine());
        thread.start();
        RequestReport report = thread.report().get(5, java.util.concurrent.TimeUnit.SECONDS);

        thread.close();

        assertThat(report.status()).isEqualTo(RequestReport.Status.COMPLETED);
    }
}
