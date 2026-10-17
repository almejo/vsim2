package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.inline.InlineTextEditor;

import java.util.Collection;
import java.util.function.BooleanSupplier;

/**
 * The order of a window close or an exit (M6.8): every open inline edit is finished first (confirmed when its text is
 * valid, cancelled otherwise), then the save question is asked, so «¿Guardar cambios?» sees a half-typed edit.
 */
public final class CloseSequence {

    private CloseSequence() {
    }

    /** Finishes the edits of {@code editors}, then asks {@code saveQuestion}; false means the user cancelled. */
    public static boolean confirm(Collection<InlineTextEditor> editors, BooleanSupplier saveQuestion) {
        editors.forEach(InlineTextEditor::confirmOrCancel);
        return saveQuestion.getAsBoolean();
    }
}
