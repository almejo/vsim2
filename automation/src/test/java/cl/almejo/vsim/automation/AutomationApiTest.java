package cl.almejo.vsim.automation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomationApiTest {

    private static final DocumentSummary ADDER = new DocumentSummary(new DocumentId("document-1"), "sumador.vsim",
            new DocumentVersion(3), true);

    @Test
    void listsTheDocumentsTheEditorHasOpen() {
        AutomationApi api = new AutomationApi(new FakeHost(List.of(ADDER)));

        assertThat(api.listDocuments()).containsExactly(ADDER);
    }

    @Test
    void anEditorThatDoesNotAnswerInTimeGivesAnUnknownOutcome() {
        AutomationHost silentHost = new AutomationHost() {
            @Override
            public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
                return new CompletableFuture<>();
            }
        };
        AutomationApi api = new AutomationApi(silentHost, Duration.ofMillis(20));

        assertThatThrownBy(api::listDocuments)
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.OUTCOME_UNKNOWN));
    }

    @Test
    void aRefusalOnTheEditorThreadReachesTheCallerAsItIs() {
        AutomationException refusal = new AutomationException(AutomationError.DOCUMENT_CLOSED, "closed",
                Map.of("document", "document-1"));
        AutomationHost refusingHost = new AutomationHost() {
            @Override
            public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
                return CompletableFuture.failedFuture(refusal);
            }
        };

        assertThatThrownBy(new AutomationApi(refusingHost)::listDocuments).isSameAs(refusal);
    }

    @Test
    void undoingATaskThatNeverBeganIsAnInvalidReference() {
        AutomationApi api = new AutomationApi(new FakeHost(List.of()));

        assertThatThrownBy(() -> api.undoTask("task-1"))
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }

    @Test
    void aHostWithoutEditableCircuitsSaysTheCircuitIsClosed() {
        AutomationApi api = new AutomationApi(new FakeHost(List.of(ADDER)));

        assertThatThrownBy(() -> api.beginTask(ADDER.id(), "Sumador", List.of()))
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.DOCUMENT_CLOSED));
    }

    @Test
    void aListenerCanBeTakenBack() {
        AutomationApi api = new AutomationApi(new FakeHost(List.of()));
        AutomationListener listener = new AutomationListener() {
            @Override
            public void taskStarted(TaskInfo task) {
            }

            @Override
            public void taskEnded(TaskInfo task, java.util.Set<String> changedElementIds) {
            }

            @Override
            public void taskStopped(TaskInfo task) {
            }
        };

        Runnable removal = api.addListener(listener);
        assertThat(api.listenerCount()).isEqualTo(1);
        removal.run();

        assertThat(api.listenerCount()).isZero();
    }

    /** Runs the work at once on the calling thread, over a fixed list of documents. */
    static final class FakeHost extends FakeAutomationHost {

        private final List<DocumentSummary> documents;

        FakeHost(List<DocumentSummary> documents) {
            this.documents = documents;
        }

        @Override
        protected List<DocumentSummary> openDocuments() {
            return documents;
        }
    }
}
