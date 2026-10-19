package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.document.CircuitDocument;

/** File and window operations the editor delegates to its window/application. */
public interface WindowCommands {
    void newDocument();

    void open();

    void save();

    void saveAs();

    void closeWindow();

    void exit();

    /** Ventana ▸ Nueva vista: another view of this document. Nothing happens outside a real window. */
    default void newView() {
    }

    /**
     * Archivo ▸ Ejemplos (M6.1-A): shows {@code document} in a new window as an untitled, unmodified document.
     * Nothing happens outside a real window.
     */
    default void openUntitled(CircuitDocument document) {
    }

    /**
     * «Circuito desde tabla de verdad» ▸ Crear (circuito nuevo): a new window with an untitled document named
     * {@code name} whose first undo step is {@code creation}, with {@code notice} in its status bar (none when empty).
     * Nothing happens outside a real window.
     */
    default void openNewCircuit(String name, Command creation, String notice) {
    }
}
