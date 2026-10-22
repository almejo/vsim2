package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Tells the automation layer when a circuit's window changes its «Presentar» mode or the circuit closes (IA.2 Task 1):
 * a step waiting for «Siguiente» is then written (the user switched to «De corrido» or left «Presentar») or dropped
 * (the window closed). {@link #refresh()} runs on the EDT after every preference change and window close; what it
 * finds is told off the EDT, since the automation layer waits for the editor thread.
 */
public final class PresentationModeWatcher {

    /** What the watcher tells: the automation API in VSim, a recorder in tests. */
    public interface Target {

        void presentationChanged(DocumentId document, PresentationSettings settings);

        void documentClosed(DocumentId document);

        static Target of(AutomationApi api) {
            Objects.requireNonNull(api, "api");
            return new Target() {
                @Override
                public void presentationChanged(DocumentId document, PresentationSettings settings) {
                    api.presentationChanged(document, settings);
                }

                @Override
                public void documentClosed(DocumentId document) {
                    api.documentClosed(document);
                }
            };
        }
    }

    private final Supplier<List<OpenDocument>> openDocuments;
    private final Function<OpenDocument, PresentationSettings> settingsOf;
    private final Function<OpenDocument, DocumentId> idOf;
    private final Target target;
    private final Executor background;
    /** EDT only: the mode each open circuit had at the last refresh. */
    private final Map<DocumentId, PresentationSettings> known = new HashMap<>();

    /**
     * @param settingsOf the mode of a circuit's window (its first one); called on the EDT
     * @param idOf       the id the AI knows a circuit by; called on the EDT
     * @param background where the target is told: never the EDT
     */
    public PresentationModeWatcher(Supplier<List<OpenDocument>> openDocuments,
                                   Function<OpenDocument, PresentationSettings> settingsOf,
                                   Function<OpenDocument, DocumentId> idOf, Target target, Executor background) {
        this.openDocuments = Objects.requireNonNull(openDocuments, "openDocuments");
        this.settingsOf = Objects.requireNonNull(settingsOf, "settingsOf");
        this.idOf = Objects.requireNonNull(idOf, "idOf");
        this.target = Objects.requireNonNull(target, "target");
        this.background = Objects.requireNonNull(background, "background");
    }

    /** EDT: compares every open circuit's mode with the last refresh and tells what changed or closed. */
    public void refresh() {
        Set<DocumentId> open = new HashSet<>();
        for (OpenDocument document : openDocuments.get()) {
            DocumentId id = idOf.apply(document);
            open.add(id);
            PresentationSettings settings = settingsOf.apply(document);
            PresentationSettings before = known.put(id, settings);
            if (before != null && !before.equals(settings)) {
                background.execute(() -> target.presentationChanged(id, settings));
            }
        }
        for (DocumentId closed : Set.copyOf(known.keySet())) {
            if (!open.contains(closed)) {
                known.remove(closed);
                background.execute(() -> target.documentClosed(closed));
            }
        }
    }
}
