package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.StatusBar;

import java.util.List;
import java.util.function.Supplier;

/**
 * A recovery copy that cannot be written (full disk, unwritable folder) is said once in the status bar of every
 * open tab, and the notice stays until a write succeeds or the user dismisses it: the user can still save the
 * circuit, but a crash would lose what changed since. EDT only.
 */
final class RecoveryWriteNotice {

    /** The owner key of this notice in a status bar: «cannot save» has its own, and the two never hide each other. */
    static final String OWNER = "recoveryWrite";

    private final Supplier<List<StatusBar>> statusBars;

    /** {@code statusBars} gives the status bar of every tab, in every window. */
    RecoveryWriteNotice(Supplier<List<StatusBar>> statusBars) {
        this.statusBars = statusBars;
    }

    void cannotWrite() {
        String text = Messages.text("autosave.writeFailed");
        String dismissText = Messages.text("autosave.noCopy.dismiss");
        for (StatusBar statusBar : statusBars.get()) {
            statusBar.showLastingNotice(OWNER, text, dismissText, this::canWriteAgain);
        }
    }

    void canWriteAgain() {
        statusBars.get().forEach(statusBar -> statusBar.hideLastingNotice(OWNER));
    }
}
