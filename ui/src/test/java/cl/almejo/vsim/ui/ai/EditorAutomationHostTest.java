package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentSummary;
import cl.almejo.vsim.automation.EditorAccess;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditorAutomationHostTest {

    private final List<OpenDocument> open = new ArrayList<>();
    private final EditorAutomationHost host = new EditorAutomationHost(() -> List.copyOf(open));

    @AfterEach
    void closeDocuments() {
        open.forEach(OpenDocument::close);
    }

    @Test
    void theWorkRunsOnTheEditorThread() throws Exception {
        assertThat(host.onEditorThread(access -> SwingUtilities.isEventDispatchThread()).get(5, TimeUnit.SECONDS))
                .isTrue();
    }

    @Test
    void workStillQueuedWhenTheAiIsTurnedOffIsRefusedAtOnceAndNeverRuns() throws Exception {
        java.util.concurrent.CountDownLatch editorBusy = new java.util.concurrent.CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                editorBusy.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.CompletableFuture<Boolean> queued = host.onEditorThread(access -> ran.getAndSet(true));
        java.util.concurrent.CompletableFuture<Boolean> edit = host.onCircuit(
                new cl.almejo.vsim.automation.DocumentId("circuit-1"), circuit -> ran.getAndSet(true));

        host.refuseQueuedWork();

        for (java.util.concurrent.CompletableFuture<Boolean> future : List.of(queued, edit)) {
            assertThat(future).isCompletedExceptionally();
            assertThatThrownBy(future::join).hasCauseInstanceOf(AutomationException.class)
                    .cause().satisfies(cause -> assertThat(((AutomationException) cause).error())
                            .isEqualTo(AutomationError.NOT_ALLOWED));
        }
        editorBusy.countDown();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(ran).isFalse();
        assertThat(host.onEditorThread(access -> "later")).failsWithin(5, TimeUnit.SECONDS);

        host.acceptQueuedWork();
        assertThat(host.onEditorThread(access -> "again").get(5, TimeUnit.SECONDS)).isEqualTo("again");
    }

    @Test
    void whatTheWorkThrowsFailsTheFuture() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> host.onEditorThread(access -> {
            throw failure;
        }).get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCause(failure);
    }

    @Test
    void eachOpenCircuitHasItsNameVersionAndAnIdThatStaysTheSame() throws Exception {
        OpenDocument adder = document("Sumador");
        OpenDocument counter = document("Contador");

        List<DocumentSummary> first = list();
        List<DocumentSummary> second = list();

        assertThat(first).extracting(DocumentSummary::name).containsExactly("Sumador", "Contador");
        assertThat(first).extracting(DocumentSummary::id).doesNotHaveDuplicates();
        assertThat(second).extracting(DocumentSummary::id).isEqualTo(first.stream().map(DocumentSummary::id).toList());
        assertThat(first.get(0).version().value()).isEqualTo(adder.contentVersion());
        assertThat(first.get(1).modified()).isEqualTo(counter.isModified());
    }

    @Test
    void anEditMovesTheVersionAndMarksTheCircuitModified() throws Exception {
        OpenDocument adder = document("Sumador");
        DocumentSummary before = list().get(0);

        SwingUtilities.invokeAndWait(() -> place(adder, StandardComponentTypes.NOT, 0, 0, Map.of()));
        DocumentSummary after = list().get(0);

        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.version().value()).isGreaterThan(before.version().value());
        assertThat(after.modified()).isTrue();
    }

    @Test
    void aCircuitOpenedLaterNeverGetsTheIdOfAClosedOne() throws Exception {
        OpenDocument adder = document("Sumador");
        DocumentSummary closed = list().get(0);
        open.remove(adder);
        adder.close();

        document("Contador");

        assertThat(list()).extracting(DocumentSummary::id).doesNotContain(closed.id());
    }

    private OpenDocument document(String name) {
        CircuitDocument circuit = new CircuitDocument();
        circuit.setName(name);
        OpenDocument document = new OpenDocument(circuit, StandardComponentTypes.registry(), null, false);
        open.add(document);
        return document;
    }

    private List<DocumentSummary> list() throws Exception {
        return host.onEditorThread(EditorAccess::openDocuments).get(5, TimeUnit.SECONDS);
    }
}
