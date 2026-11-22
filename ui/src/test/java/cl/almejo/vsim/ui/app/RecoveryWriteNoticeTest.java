package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.StatusBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The recovery-write notice and the «cannot save» notice share a status bar and never hide each other. */
class RecoveryWriteNoticeTest {

    private final StatusBar bar = new StatusBar();
    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final RecoveryWriteNotice writeNotice = new RecoveryWriteNotice(() -> List.of(bar));
    private final CannotSaveNotices cannotSave = new CannotSaveNotices(document -> List.of(bar));

    @BeforeEach
    void speakSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        circuit.close();
    }

    @Test
    void bothNoticesAreSaidAtOnce() {
        cannotSave.cannotSave(circuit);
        writeNotice.cannotWrite();

        assertThat(bar.text()).contains("ni guardarlo").contains(Messages.text("autosave.writeFailed"));
    }

    @Test
    void writingAgainDoesNotHideTheCannotSaveNotice() {
        cannotSave.cannotSave(circuit);
        writeNotice.cannotWrite();

        writeNotice.canWriteAgain();

        assertThat(bar.text()).contains("ni guardarlo").doesNotContain(Messages.text("autosave.writeFailed"));
    }

    @Test
    void beingAbleToSaveAgainDoesNotHideTheWriteNotice() {
        writeNotice.cannotWrite();
        cannotSave.cannotSave(circuit);

        cannotSave.canSaveAgain(circuit);

        assertThat(bar.text()).contains(Messages.text("autosave.writeFailed")).doesNotContain("ni guardarlo");
    }

    @Test
    void dismissingOneLeavesTheOther() {
        cannotSave.cannotSave(circuit);
        writeNotice.cannotWrite();

        bar.lastingNoticeDismiss(RecoveryWriteNotice.OWNER).doClick();

        assertThat(bar.text()).contains("ni guardarlo").doesNotContain(Messages.text("autosave.writeFailed"));
    }
}
