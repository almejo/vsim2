package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationHost;
import cl.almejo.vsim.automation.EditorAccess;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** An automation host with no open documents, for the tests of the AI server and its panels. */
final class NoDocuments implements AutomationHost {

    @Override
    public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
        return CompletableFuture.completedFuture(work.apply(List::of));
    }
}
