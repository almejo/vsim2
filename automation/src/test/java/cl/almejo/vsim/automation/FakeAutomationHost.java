package cl.almejo.vsim.automation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The base of the automation tests' fake hosts: the editor thread is the calling thread, the work runs at once and
 * its outcome comes back as a completed (or failed) future. A fake overrides {@link #openDocuments} for what the
 * editor has open, {@link #run} to serialise the work, and the host methods it fakes.
 */
public abstract class FakeAutomationHost implements AutomationHost {

    /** The circuits the editor has open: none unless a fake says otherwise. */
    protected List<DocumentSummary> openDocuments() {
        return List.of();
    }

    /** Runs {@code work} where the editor thread would: at once, its failure becoming a failed future. */
    protected <T> CompletableFuture<T> run(Supplier<T> work) {
        try {
            return CompletableFuture.completedFuture(work.get());
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
        return run(() -> work.apply(this::openDocuments));
    }
}
