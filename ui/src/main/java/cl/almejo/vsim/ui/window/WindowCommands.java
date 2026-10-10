package cl.almejo.vsim.ui.window;

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
}
