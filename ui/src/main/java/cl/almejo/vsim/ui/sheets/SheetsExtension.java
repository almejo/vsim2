package cl.almejo.vsim.ui.sheets;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adds «Mostrar hojas de impresión» (Ver and the toolbar) and the sheets drawn over the circuit to every editor view
 * (sheets spec §0a, §5). One per application: it holds the application's {@link SheetAnchors} and knows every view's
 * {@link SheetsFeatures}, so a view that stops showing the sheets can tell whether another view of the same circuit
 * still shows them (then the anchor stays) or none does (then it is cleared and the next showing lays the sheets out
 * afresh).
 */
public final class SheetsExtension implements EditorExtension {

    private final SheetAnchors anchors;
    private final SheetsFeatures.MenuShower menuShower;
    /** The features of every view installed now, in installation order. EDT only. */
    private final List<SheetsFeatures> installed = new ArrayList<>();

    public SheetsExtension(SheetAnchors anchors) {
        this(anchors, (menu, invoker, x, y) -> menu.show(invoker, x, y));
    }

    /** With {@code menuShower} instead of showing the quick options popup (headless tests record it). */
    SheetsExtension(SheetAnchors anchors, SheetsFeatures.MenuShower menuShower) {
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.menuShower = Objects.requireNonNull(menuShower, "menuShower");
    }

    @Override
    public Runnable install(EditorView view) {
        return installFeatures(view)::dispose;
    }

    /** Installs the sheets on {@code view}; {@link SheetsFeatures#dispose()} takes them back (and out of here). */
    SheetsFeatures installFeatures(EditorView view) {
        SheetsFeatures features = new SheetsFeatures(view, anchors, this, menuShower);
        installed.add(features);
        features.refresh();
        return features;
    }

    /** {@code features} went away: it no longer counts as a view that may show its circuit's sheets. */
    void forget(SheetsFeatures features) {
        installed.remove(features);
    }

    /**
     * Clears {@code document}'s anchor when no installed view of it shows the sheets any more (the preference or the
     * AI's layer turned off, the last showing window closed); another view still showing them keeps it.
     */
    void releaseIfNoViewShows(CircuitDocument document) {
        for (SheetsFeatures features : installed) {
            if (features.document() == document && features.isShowing()) {
                return;
            }
        }
        anchors.clear(document);
    }
}
