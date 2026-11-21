package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.ui.i18n.Messages;

/**
 * What the window's Cortar, Copiar, Pegar, Duplicar and Seleccionar todo (Editar, Ctrl+X/C/V/D/A) act on while a view
 * shows another content instead of its circuit panes: the states and arrows of a machine's diagram (hand test of
 * hito 2). Cortar, Copiar and Duplicar are enabled as {@link #canCopy()} says, Pegar as {@link #canPaste()} says. EDT
 * only.
 */
public interface ContentClipboard {

    /** Whether there is something to copy now. */
    boolean canCopy();

    /** Puts what is selected on the clipboard. */
    void copy();

    /** Puts what is selected on the clipboard and deletes it, as one undo step. */
    void cut();

    /** Whether the clipboard holds something {@link #paste()} takes now (Pegar is off otherwise, as in a circuit). */
    boolean canPaste();

    /** Why Pegar is off now: by default, that nothing was copied. */
    default String cannotPasteReason() {
        return Messages.text("reason.nothingCopied");
    }

    /** Adds what the clipboard holds, as one undo step; says why on the status line when it cannot. */
    void paste();

    /** Adds a copy of what is selected beside it, as one undo step, without touching the clipboard. */
    void duplicate();

    /** Selects everything. */
    void selectAll();
}
