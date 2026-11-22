package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.StatusBar;

import java.util.List;
import java.util.function.Function;

/**
 * APP-5: a circuit that cannot be written (a piece whose component is missing) has no recovery copy and cannot be
 * saved either. Every tab of that circuit says so in its status bar, and keeps saying it until the user dismisses it
 * (in any of its tabs) or the circuit can be written again: a state that lasts for hours is not a notice of a few
 * seconds that the next notice replaces. EDT only.
 */
final class CannotSaveNotices {

    /** The owner key of this notice in a status bar: the recovery-write notice has its own, and they never hide each other. */
    static final String OWNER = "cannotSave";

    private final Function<OpenDocument, List<StatusBar>> statusBarsOf;

    /** {@code statusBarsOf} gives the status bars of every tab that shows a document, in every window. */
    CannotSaveNotices(Function<OpenDocument, List<StatusBar>> statusBarsOf) {
        this.statusBarsOf = statusBarsOf;
    }

    /** {@code document} cannot be written: its tabs say so until dismissed. */
    void cannotSave(OpenDocument document) {
        String text = Messages.format("autosave.noCopy", document.displayName());
        String dismissText = Messages.text("autosave.noCopy.dismiss");
        for (StatusBar statusBar : statusBarsOf.apply(document)) {
            statusBar.showLastingNotice(OWNER, text, dismissText, () -> canSaveAgain(document));
        }
    }

    /** {@code document} can be written again, or the user dismissed the notice: none of its tabs says it any more. */
    void canSaveAgain(OpenDocument document) {
        statusBarsOf.apply(document).forEach(statusBar -> statusBar.hideLastingNotice(OWNER));
    }
}
