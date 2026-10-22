package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.AppThemes;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.util.List;

import static cl.almejo.vsim.ui.ai.present.FakePresentationEvents.step;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * IA.3 «Speed»: when «De corrido» stops after a failed check, the panel says so plainly under its header in the
 * accent and the current card shows the failed check; it goes when the AI checks again and passes or the user
 * presses «Siguiente».
 */
class PresentationHeldNoticeTest {

    private static final TaskInfo TASK = new TaskInfo("t1", new DocumentId("d1"), "Sumador de 4 bits");
    private static final List<TaskStage> STAGES = List.of(
            new TaskStage("Sumador de 1 bit", List.of("Pongo las XOR", "Pongo las AND")),
            new TaskStage("Sumador de 4 bits", List.of("Pongo cuatro sumadores", "Encadeno los acarreos")));
    private static final String HELD = "Me detuve: falló la comprobación de «Sumador de 1 bit» · Siguiente para seguir";
    private static final String FAILED_CHECK = "✗ Comprobación de «Sumador de 1 bit»: la fila 3 da 0";

    private final AppPreferences windowPreferences = AppPreferences.inMemory().layered();
    private final RecordingControls controls = new RecordingControls();
    private final PresentedTask task = new PresentedTask(TASK);
    private PresentationPanel panel;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        windowPreferences.setAiPace(PresentationPace.CONTINUOUS);
        panel = new PresentationPanel(windowPreferences, controls, () -> { });
        task.planChanged(STAGES, 0, 2);
        panel.show(task, false);
    }

    @AfterEach
    void close() {
        panel.dispose();
    }

    /** The first stage's check failed and VSim held the next stage's first step instead of applying it. */
    private void heldAfterAFailedCheck() {
        task.stageChecked(0, false, "la fila 3 da 0");
        task.stepProposed(step("t1", 1, 0, "Pongo cuatro sumadores"));
        panel.show(task, false);
    }

    @Test
    void deCorridoHeldAfterAFailedCheckSaysSoInTheAccentAndTheCurrentCardShowsTheCheck() {
        assertThat(panel.heldNotice().isVisible()).isFalse();

        heldAfterAFailedCheck();

        assertThat(panel.heldNotice().isVisible()).isTrue();
        assertThat(panel.heldNotice().getText()).isEqualTo(HELD);
        assertThat(panel.heldNotice().tone()).isEqualTo(StageCard.Tone.ACCENT);
        assertThat(panel.heldNotice().getFont().isBold()).isTrue();
        assertThat(panel.cards().get(1).lineTexts()).contains(FAILED_CHECK);
        assertThat(panel.cards().get(1).lines()).filteredOn(line -> line.getText().equals(FAILED_CHECK))
                .allSatisfy(line -> assertThat(line.tone()).isEqualTo(StageCard.Tone.ERROR));
    }

    @Test
    void siguienteTakesTheNoticeAwayAndItIsNotSaidAgainForTheSameCheck() {
        heldAfterAFailedCheck();

        panel.nextButton().doClick();

        assertThat(controls.calls).containsExactly("next t1");
        assertThat(panel.heldNotice().isVisible()).isFalse();
        task.stepApplied(step("t1", 1, 0, "Pongo cuatro sumadores"));
        task.stepProposed(step("t1", 1, 1, "Encadeno los acarreos"));
        panel.show(task, false);
        assertThat(panel.heldNotice().isVisible()).isFalse();
    }

    @Test
    void aCheckThatPassesAgainTakesTheNoticeAway() {
        heldAfterAFailedCheck();

        task.stageChecked(0, true, "cumple la tabla");
        panel.show(task, false);

        assertThat(panel.heldNotice().isVisible()).isFalse();
        assertThat(panel.cards().get(1).lineTexts()).doesNotContain(FAILED_CHECK);
    }

    @Test
    void pasoAPasoWaitsAtEveryStepSoNothingIsSaidButTheCardStillShowsTheCheck() {
        windowPreferences.setAiPace(PresentationPace.STEP_BY_STEP);

        heldAfterAFailedCheck();

        assertThat(panel.heldNotice().isVisible()).isFalse();
        assertThat(panel.cards().get(1).lineTexts()).contains(FAILED_CHECK);
    }

    @Test
    void theNoticeShowsWhenThePanelIsFoldedToo() {
        heldAfterAFailedCheck();

        panel.foldButton().doClick();

        assertThat(panel.body().isVisible()).isFalse();
        assertThat(panel.heldNotice().isVisible()).isTrue();
    }

    @Test
    void theAccentReadsOnEveryTheme() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                assertThat(ReadableColors.contrast(StageCard.Tone.ACCENT.color(), ReadableColors.panelBackground()))
                        .as(theme.getName()).isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }
}
