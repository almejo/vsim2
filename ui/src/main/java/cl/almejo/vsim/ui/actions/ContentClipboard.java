package cl.almejo.vsim.ui.actions;

/**
 * What the window's Cortar, Copiar, Pegar, Duplicar and Seleccionar todo (Editar, Ctrl+X/C/V/D/A) act on while a view
 * shows another content instead of its circuit panes: the states and arrows of a machine's diagram (hand test of
 * hito 2). Cortar, Copiar and Duplicar are enabled as {@link #canCopy()} says. EDT only.
 */
public interface ContentClipboard {

    /** Whether there is something to copy now. */
    boolean canCopy();

    /** Puts what is selected on the clipboard. */
    void copy();

    /** Puts what is selected on the clipboard and deletes it, as one undo step. */
    void cut();

    /** Adds what the clipboard holds, as one undo step; says why on the status line when it cannot. */
    void paste();

    /** Adds a copy of what is selected beside it, as one undo step, without touching the clipboard. */
    void duplicate();

    /** Selects everything. */
    void selectAll();
}
