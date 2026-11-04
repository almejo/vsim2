package cl.almejo.vsim.ui.logisim;

import java.awt.Component;

/** The seam through which the import talks to the user; tests record instead of showing dialogs. */
interface LogisimImportDialogs {

    /** Why a file could not be imported: {@code message} in plain words, {@code details} technical (may be empty). */
    void problem(Component parent, String title, String message, String details);

    /**
     * The notes of an import or export that went through; {@code summary} says which file and how many notes. The
     * window does not block the editor: a line that names a part goes to it through {@code host}, which also
     * remembers the window's size.
     */
    void report(Component parent, String title, String summary, ImportReport report, ReportHost host);

    /** The «Importando…» dialog, shown only when the import is slow. */
    LogisimImportJob.Progress progress(Component parent);
}
