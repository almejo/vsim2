package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.CircuitDocument;

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

    /** Ventana ▸ «Cerrar los componentes de este circuito»: the component tabs of the active tab's group. */
    default void closeComponents() {
    }
}
