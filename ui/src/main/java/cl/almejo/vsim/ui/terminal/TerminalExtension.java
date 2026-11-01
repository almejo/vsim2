package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Gives every editor view its Terminals and Teclados (spec §8.4, §8.6, §8.9, §9): a double click on a Terminal's
 * screen and its context menu open its window; the context menus clear a screen, paste into a Teclado or empty its
 * queue; the canvas tooltip of a written cell; a Terminal with «Mostrar: En el canvas y en una ventana» opens its
 * window with the document and when it is placed. The frames, windows, saved output and sounds belong to the document
 * ({@link TerminalDocument}), shared by its views; the last view to close takes them down. The returned runnable takes
 * everything back.
 */
public final class TerminalExtension implements EditorExtension {

    /** The value of «Mostrar» that also shows a window. */
    private static final String SHOW_WINDOW = "window";

    /** One document's features and how many views use them. */
    private static final class Shared {
        private final TerminalDocument terminals;
        private int views;

        private Shared(TerminalDocument terminals) {
            this.terminals = terminals;
        }
    }

    private final TerminalServices services;
    private final Map<OpenDocument, Shared> documents = new IdentityHashMap<>();
    /** The files «Guardar salida» writes, across every open circuit. */
    private final TerminalOutputClaims outputClaims = new TerminalOutputClaims();

    public TerminalExtension() {
        this(TerminalServices.standard());
    }

    public TerminalExtension(TerminalServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public Runnable install(EditorView view) {
        OpenDocument document = view.openDocument();
        TerminalDocument terminals = acquire(document, view);
        TerminalWindows windows = terminals.windows();
        List<Runnable> removers = new ArrayList<>();
        TerminalWindowOwner owner = new TerminalWindowOwner(view,
                Optional.ofNullable(SwingUtilities.getWindowAncestor(view)), view.statusBar()::showNotice,
                view.services().appearance()::scheme);
        view.setTerminalEditor(component -> windows.open(component.id().value(), ownerOf(view, owner)));
        removers.add(view::clearTerminalEditor);
        removers.add(terminals.addNoticeListener(view.statusBar()::showNotice));
        removers.add(terminals.setOwnerWindow(() -> SwingUtilities.getWindowAncestor(view)));
        removers.add(view.labelFeatures().properties().addComponentRows(new TerminalOutputRows(document,
                terminals.recorder(), services.windows().saveChooser(), view)));
        removers.add(view.addContextMenuContributor(new TerminalContextMenu(document,
                component -> windows.open(component.id().value(), ownerOf(view, owner)),
                services.windows().clipboardText(), view.statusBar()::showNotice)));
        removers.add(view.forEachPane(pane -> pane.addToolTipOverride(gridPoint -> TerminalCellToolTip.at(
                document.document(), document.registry(), document.latestSnapshot(), gridPoint))));
        DocumentListener placed = (changed, change) -> openPlacedWindows(changed, change, windows, view, owner);
        document.document().addListener(placed);
        removers.add(() -> document.document().removeListener(placed));
        services.later().execute(() -> {
            Shared shared = documents.get(document);
            if (shared != null && shared.terminals == terminals) {
                openShownWindows(document, windows, view, owner);
            }
        });
        removers.add(() -> windows.closeOpenedBy(view));
        return () -> {
            removers.reversed().forEach(Runnable::run);
            release(document);
        };
    }

    private TerminalDocument acquire(OpenDocument document, EditorView view) {
        Shared shared = documents.computeIfAbsent(document, key -> new Shared(new TerminalDocument(key, services,
                new TerminalWindowPreferences(view.services().preferences().terminalWindowsNode()), outputClaims)));
        shared.views++;
        return shared.terminals;
    }

    private void release(OpenDocument document) {
        Shared shared = documents.get(document);
        if (shared != null && --shared.views == 0) {
            documents.remove(document);
            shared.terminals.dispose();
        }
    }

    /** The owner with the editor window the view is in now (it may not have had one when the view was built). */
    private static TerminalWindowOwner ownerOf(EditorView view, TerminalWindowOwner owner) {
        return new TerminalWindowOwner(owner.key(), Optional.ofNullable(SwingUtilities.getWindowAncestor(view)),
                owner.notices(), owner.scheme());
    }

    /** «Mostrar: En el canvas y en una ventana»: the windows of the Terminals already on the circuit. */
    private static void openShownWindows(OpenDocument document, TerminalWindows windows, EditorView view,
                                         TerminalWindowOwner owner) {
        for (Component component : List.copyOf(document.document().components())) {
            if (showsWindow(document, component)) {
                windows.open(component.id().value(), ownerOf(view, owner));
            }
        }
    }

    /** A Terminal placed with «Mostrar: En el canvas y en una ventana» opens its window. */
    private static void openPlacedWindows(CircuitDocument changed, DocumentChange change, TerminalWindows windows,
                                          EditorView view, TerminalWindowOwner owner) {
        for (ElementId added : change.added()) {
            changed.component(added).filter(component -> showsWindow(view.openDocument(), component))
                    .filter(component -> !windows.isOpen(component.id().value()))
                    .ifPresent(component -> windows.open(component.id().value(), ownerOf(view, owner)));
        }
    }

    private static boolean showsWindow(OpenDocument document, Component component) {
        return component.typeId().equals(StandardComponentTypes.TERMINAL) && SHOW_WINDOW.equals(document.registry()
                .typeOf(component).resolveParameters(component.parameters()).get(TerminalTypes.SHOW));
    }
}
