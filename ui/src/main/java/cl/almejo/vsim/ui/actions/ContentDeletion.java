package cl.almejo.vsim.ui.actions;

/**
 * What the window's «Borrar» (Editar ▸ Borrar, Supr) deletes while a view shows another content instead of its circuit
 * panes: the selected states and arrows of a machine's diagram (spec 2026-11-14 §1 «Drawing»). EDT only.
 */
public interface ContentDeletion {

    /** Whether there is something to delete now (Borrar is enabled only then). */
    boolean canDelete();

    /** Deletes what is selected, as one undo step. */
    void delete();
}
