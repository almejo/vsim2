package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.inline.InlineTextEditor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The order of a window close or an exit (M6.8): every open inline edit is finished first (confirmed when its text is
 * valid, cancelled otherwise), then the drafts of the closing views are decided and written ({@link DraftGuards}, hito
 * 4 of the machines), then the save question is asked, so «¿Guardar cambios?» sees a half-typed edit and an applied
 * draft; a Cancelar there takes the drafts' steps back.
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
     * Finishes the edits of {@code editors}, then asks the {@code drafts} of the views that close what to do (a
     * machine's test mode asks «Aplicar / Dejar como estaba / Cancelar») and writes it, then asks {@code saveQuestion},
     * so the save question sees what was applied; false means the user cancelled (at a draft's question or the save
     * question). A Cancelar at the save question takes back what the drafts wrote, in reverse order, and their tabs
     * go on as they were (review of hito 4, finding 3).
     */
    public static boolean confirm(Collection<InlineTextEditor> editors, Collection<DraftGuards> drafts,
                                  BooleanSupplier saveQuestion) {
        editors.forEach(InlineTextEditor::confirmOrCancel);
        Optional<List<DraftGuard.Closing>> closings = DraftGuards.decideClosing(drafts);
        if (closings.isEmpty()) {
            return false;
        }
        closings.get().forEach(DraftGuard.Closing::write);
        if (!saveQuestion.getAsBoolean()) {
            closings.get().reversed().forEach(DraftGuard.Closing::takeBack);
            return false;
        }
        closings.get().forEach(DraftGuard.Closing::finish);
        return true;
    }
}
