package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;

import java.util.Optional;

/** File and window operations the editor delegates to its window/application. */
public interface WindowCommands {
    void newDocument();

    void open();

    void save();

    void saveAs();

    /** Archivo ▸ «Cerrar ventana» (Ctrl+Mayús+W): every tab of the window, asking once per document it last holds. */
    void closeWindow();

    void exit();

    /**
     * Ventana ▸ «Nueva pestaña del mismo circuito»: another tab on the active tab's place, in its group. Nothing
     * happens outside a real window.
     */
    default void newView() {
    }

    /**
     * Archivo ▸ Ejemplos (M6.1-A): shows {@code document} in a new window as an untitled, unmodified document.
     * Nothing happens outside a real window.
     */
    default void openUntitled(CircuitDocument document) {
    }

    /**
     * Like {@link #openUntitled}, and says which editor view shows the document, for what must talk to it afterwards
     * (the import's report goes to parts of it). The window shows {@code windowName} until the document is saved,
     * while the circuit keeps its own name (an import: the {@code .circ} file's name over Logisim's main circuit's).
     * Empty outside a real window.
     */
    default Optional<EditorView> showUntitled(CircuitDocument document, String windowName) {
        openUntitled(document);
        return Optional.empty();
    }

    /**
     * «Circuito desde tabla de verdad» ▸ Crear (circuito nuevo): a new window with an untitled document named
     * {@code name} whose first undo step is {@code creation}, with {@code notice} in its status bar (none when empty).
     * Nothing happens outside a real window.
     */
    default void openNewCircuit(String name, Command creation, String notice) {
    }

    /** Ventana ▸ «Nueva ventana con este circuito»: a new window with a new main tab of this document. */
    default void newWindowWithCircuit() {
    }

    /** Ventana ▸ «Mover el grupo a una ventana nueva»: the active tab's group goes to a window of its own. */
    default void moveGroupToNewWindow() {
    }

    /** Ventana ▸ «Plegar / desplegar el grupo» of the active tab. */
    default void toggleFold() {
    }

    /** Ventana ▸ «Pestaña siguiente» (Ctrl+Tab), wrapping. */
    default void nextTab() {
    }

    /** Ventana ▸ «Pestaña anterior» (Ctrl+Mayús+Tab), wrapping. */
    default void previousTab() {
    }

    /** Archivo / Ventana ▸ «Cerrar pestaña» (Ctrl+W): closes the active tab, asking to save only as spec §4.5 says. */
    default void closeTab() {
    }

    /**
     * Enters definition {@code definitionId} of {@code document} (spec §4.4): a component tab in the document's group,
     * showing the instance at {@code contextPath} (empty: «Sin valores»); the group's tab of that definition most
     * recently active is focused and re-contexted instead when there is one. Nothing happens outside a real window.
     */
    default void enter(OpenDocument document, String definitionId, Optional<String> contextPath) {
        enter(document, definitionId, contextPath, Optional.empty());
    }

    /**
     * As {@link #enter(OpenDocument, String, Optional)}; a new tab's view starts at {@code from} (where the instance
     * was drawn) and travels to the whole body, when entering animates.
     */
    default void enter(OpenDocument document, String definitionId, Optional<String> contextPath,
                       Optional<ViewState> from) {
    }

    /**
     * Brings the place to the front: {@code definitionId} empty is the document's main tab, else its component tab
     * showing {@code contextPath} (as {@link #enter}); the view that shows it. Empty outside a real window. The place
     * bar's document crumb; to show a part there, use {@link #goToPart}.
     */
    default Optional<EditorView> showPlace(OpenDocument document, Optional<String> definitionId,
                                           Optional<String> contextPath) {
        return Optional.empty();
    }

    /**
     * Shows part {@code part} of {@code document} where it is (F8 and the Problemas list, spec §6.7; «Ver la pieza» of
     * the place bar and the circuit tree, spec §5, through {@link SeePiece}): the place {@link #showPlace} brings to
     * the front, with the part selected and brought into view. False when there are no tabs to show it in (an editor
     * outside a real window): nothing happens, and the caller shows what it can where it is.
     */
    default boolean goToPart(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath,
                             ElementId part) {
        return false;
    }

    /** Ventana ▸ «Cerrar los componentes de este circuito»: the component tabs of the active tab's group. */
    default void closeComponents() {
    }
}
