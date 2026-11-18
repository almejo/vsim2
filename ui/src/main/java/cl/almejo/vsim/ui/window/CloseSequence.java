package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.inline.InlineTextEditor;

import java.util.Collection;
import java.util.function.BooleanSupplier;

/**
 * The order of a window close or an exit (M6.8): every open inline edit is finished first (confirmed when its text is
 * valid, cancelled otherwise), then the drafts of the closing views are settled ({@link DraftGuards}, hito 4 of the
 * machines), then the save question is asked, so «¿Guardar cambios?» sees a half-typed edit and an applied draft.
 */
public final class CloseSequence {

    private CloseSequence() {
    }

    /** Finishes the edits of {@code editors}, then asks {@code saveQuestion}; false means the user cancelled. */
    public static boolean confirm(Collection<InlineTextEditor> editors, BooleanSupplier saveQuestion) {
        editors.forEach(InlineTextEditor::confirmOrCancel);
        return saveQuestion.getAsBoolean();
    }

    /**
     * Finishes the edits of {@code editors}, then settles the {@code drafts} of the views that close (a machine's test
     * mode asks «Aplicar / Dejar como estaba / Cancelar»), then asks {@code saveQuestion}, so the save question sees
     * what was applied; false means the user cancelled (at a draft's question or the save question).
     */
    public static boolean confirm(Collection<InlineTextEditor> editors, Collection<DraftGuards> drafts,
                                  BooleanSupplier saveQuestion) {
        editors.forEach(InlineTextEditor::confirmOrCancel);
        if (DraftGuards.settle(drafts, DraftGuard.Reason.CLOSE) == DraftGuard.Answer.CANCEL) {
            return false;
        }
        return saveQuestion.getAsBoolean();
    }
}
