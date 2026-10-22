package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.automation.StageUndoOutcome;
import cl.almejo.vsim.automation.StageUndoOutcome.StoppedAt;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.automation.present.StoryboardState;
import cl.almejo.vsim.ui.ai.SettingsOverride;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.AppThemes;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.util.List;

import static cl.almejo.vsim.ui.ai.present.FakePresentationEvents.step;
import static org.assertj.core.api.Assertions.assertThat;

/** The floating «Presentar» panel (spec §5) over a task model a test moves along, with recording controls. */
class PresentationPanelTest {

    private static final TaskInfo TASK = new TaskInfo("t1", new DocumentId("d1"), "Sumador de 4 bits");
    private static final List<TaskStage> STAGES = List.of(
            new TaskStage("Sumador de 1 bit", List.of("Pongo las XOR", "Pongo las AND")),
            new TaskStage("Sumador de 4 bits", List.of("Pongo cuatro sumadores", "Encadeno los acarreos",
                    "Pongo las salidas")),
            new TaskStage("Componente", List.of()));

    private final AppPreferences saved = AppPreferences.inMemory();
    private final AppPreferences windowPreferences = saved.layered();
    private final RecordingControls controls = new RecordingControls();
    private final PresentedTask task = new PresentedTask(TASK);
    private int closed;
    private PresentationPanel panel;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        panel = new PresentationPanel(windowPreferences, controls, () -> closed++);
        task.planChanged(STAGES, 1, 1);
        task.stageChecked(0, true, "cumple la tabla");
        panel.show(task, false);
    }

    @AfterEach
    void close() {
        panel.dispose();
    }

    @Test
    void theHeaderNamesTheTaskAndTheStage() {
        assertThat(panel.headerText()).isEqualTo("IA · Sumador de 4 bits");
        assertThat(panel.stageText()).isEqualTo("Etapa 2 / 3");
    }

    @Test
    void doneStagesAreFoldedWithTheirCheckTheCurrentOneListsItsStepsAndFutureOnesWaitToBeDetailed() {
        List<StageCard> cards = panel.cards();

        assertThat(cards).hasSize(3);
        assertThat(cards.get(0).lineTexts()).containsExactly("1. Sumador de 1 bit", "✓ cumple la tabla");
        assertThat(cards.get(1).lineTexts()).containsExactly("2. Sumador de 4 bits", "✓ Pongo cuatro sumadores",
                "▸ Encadeno los acarreos", "· Pongo las salidas");
        assertThat(cards.get(2).lineTexts()).containsExactly("3. Componente", "se detalla al llegar");
    }

    @Test
    void doneStepsAreStruckTheCurrentOneIsBoldAndTheNextOnesAreGrey() {
        List<StageCard.Line> lines = panel.cards().get(1).lines();

        assertThat(lines.get(1).getFont().getAttributes().get(TextAttribute.STRIKETHROUGH))
                .isEqualTo(TextAttribute.STRIKETHROUGH_ON);
        assertThat(lines.get(2).getFont().isBold()).isTrue();
        assertThat(lines.get(3).tone()).isEqualTo(StageCard.Tone.MUTED);
    }

    @Test
    void aFailedCheckReadsAsAFailureInRed() {
        task.stageChecked(0, false, "la fila 3 da 0");
        panel.show(task, false);

        StageCard.Line check = panel.cards().get(0).lines().get(1);
        assertThat(check.getText()).isEqualTo("✗ la fila 3 da 0");
        assertThat(check.tone()).isEqualTo(StageCard.Tone.ERROR);
    }

    @Test
    void siguienteIsOnlyForAProposedStepAndSaysSoInTheCurrentLine() {
        assertThat(panel.nextButton().isEnabled()).isFalse();

        task.stepProposed(step("t1", 1, 1, "Encadeno los acarreos"));
        panel.show(task, false);

        assertThat(panel.nextButton().isEnabled()).isTrue();
        assertThat(panel.cards().get(1).lineTexts()).contains("▸ Encadeno los acarreos · propuesto");
        panel.nextButton().doClick();
        assertThat(controls.calls).containsExactly("next t1");
    }

    @Test
    void enterPressesSiguienteWhileThePanelHasTheFocus() {
        task.stepProposed(step("t1", 1, 1, "Encadeno los acarreos"));
        panel.show(task, false);

        Object key = panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        Action action = panel.getActionMap().get(key);
        action.actionPerformed(new ActionEvent(panel, ActionEvent.ACTION_PERFORMED, "enter"));

        assertThat(controls.calls).containsExactly("next t1");
    }

    @Test
    void theButtonsAskTheControlsForTheirTaskAndStage() {
        panel.toStageEndButton().doClick();
        panel.stopButton().doClick();
        panel.undoStageButton().doClick();

        assertThat(controls.calls).containsExactly("toStageEnd t1", "stop t1", "undoStage t1");
    }

    /** Review I2: it says what it did, and turns off once no stage has steps left. */
    @Test
    void deshacerEtapaSaysWhatItDidAndTurnsOffWhenNothingIsLeft() {
        controls.nextUndo = new StageUndoOutcome(1, 2, StoppedAt.ANOTHER_STAGE, true);
        panel.undoStageButton().doClick();
        assertThat(panel.notice().getText()).isEqualTo("Me detuve en un paso de otra etapa; pasos deshechos: 2");
        assertThat(panel.undoStageButton().isEnabled()).isTrue();

        controls.nextUndo = new StageUndoOutcome(0, 0, StoppedAt.USER_EDIT, true);
        panel.undoStageButton().doClick();
        assertThat(panel.notice().getText()).isEqualTo("Me detuve en un cambio tuyo; pasos de la IA deshechos: 0");

        controls.nextUndo = new StageUndoOutcome(0, 3, StoppedAt.NOTHING, false);
        panel.undoStageButton().doClick();
        assertThat(panel.notice().getText()).isEqualTo("Deshice la etapa 1: 3 pasos");
        assertThat(panel.undoStageButton().isEnabled()).isFalse();
    }

    @Test
    void deshacerEtapaWithNothingLeftSaysSo() {
        controls.nextUndo = StageUndoOutcome.NOTHING_TO_UNDO;

        panel.undoStageButton().doClick();

        assertThat(panel.notice().getText()).isEqualTo("Nada que deshacer");
        assertThat(panel.undoStageButton().isEnabled()).isFalse();
    }

    /** Review I1: the stages are not a storyboard (a later one is wired to an earlier one's parts). */
    @Test
    void atTheEndKeepOnlyTheResultIsOfferedOnlyWhenTheStagesAreSeparate() {
        controls.state = new StoryboardState(true, false);

        task.ended();
        panel.show(task, false);

        assertThat(controls.stateQuestions).containsExactly("t1");
        assertThat(panel.keepOnlyResultButton().isVisible()).isFalse();
        assertThat(panel.undoStageButton().isEnabled()).isTrue();
    }

    @Test
    void aRefusedKeepOnlyTheResultSaysWhyAndGoesAway() {
        task.ended();
        panel.show(task, false);
        controls.keepSucceeds = false;
        controls.state = new StoryboardState(true, false);

        panel.keepOnlyResultButton().doClick();

        assertThat(panel.notice().getText())
                .isEqualTo("Las etapas no están separadas: dejar solo el resultado rompería el circuito");
        assertThat(panel.keepOnlyResultButton().isVisible()).isFalse();
    }

    /** Review M5: «Detener» keeps the panel with «Deshacer etapa» and ×. */
    @Test
    void aStoppedTaskKeepsDeshacerEtapaAndClose() {
        task.stopped();
        panel.show(task, false);

        assertThat(panel.notice().getText()).isEqualTo("Detuviste a la IA");
        assertThat(panel.undoStageButton().isVisible()).isTrue();
        assertThat(panel.closeButton().isVisible()).isTrue();
        assertThat(panel.stopButton().isVisible()).isFalse();
    }

    @Test
    void atTheEndItOffersToKeepOnlyTheResultAndToClose() {
        assertThat(panel.keepOnlyResultButton().isVisible()).isFalse();
        assertThat(panel.closeButton().isVisible()).isFalse();

        task.ended();
        panel.show(task, false);

        assertThat(panel.keepOnlyResultButton().isVisible()).isTrue();
        assertThat(panel.closeButton().isVisible()).isTrue();
        assertThat(panel.stopButton().isEnabled()).isFalse();
        assertThat(panel.toStageEndButton().isEnabled()).isFalse();
        assertThat(panel.notice().getText()).isEqualTo("La IA terminó");
        panel.keepOnlyResultButton().doClick();
        panel.closeButton().doClick();
        assertThat(controls.calls).containsExactly("keepOnlyResult t1");
        assertThat(closed).isEqualTo(1);
    }

    @Test
    void theNoticeSaysWhenTheAiSwitchedToPresentingAndWhenAStepWasDiscarded() {
        assertThat(panel.notice().isVisible()).isFalse();

        panel.show(task, true);
        assertThat(panel.notice().getText()).isEqualTo("La IA pasó a Presentar");

        task.stepDiscarded(StepStatus.STALE);
        panel.show(task, false);
        assertThat(panel.notice().getText()).isEqualTo("Cambiaste el circuito: la IA vuelve a proponer el paso");
    }

    @Test
    void thePaceSwitchIsThePreferenceBothWays() {
        assertThat(panel.stepByStepButton().isSelected()).isTrue();

        panel.continuousButton().doClick();
        assertThat(saved.aiPace()).isEqualTo(PresentationPace.CONTINUOUS);

        saved.setAiPace(PresentationPace.STEP_BY_STEP);
        assertThat(panel.stepByStepButton().isSelected()).isTrue();
        assertThat(panel.continuousButton().isSelected()).isFalse();
    }

    @Test
    void theChosenPaceIsFilledWithTheAccentOnEveryThemeAndTheOtherIsPlain() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                saved.setAiPace(PresentationPace.CONTINUOUS);
                assertThat(panel.continuousButton().getForeground()).isEqualTo(PaceSwitch.onAccent());
                assertThat(ReadableColors.contrast(PaceSwitch.onAccent(), PaceSwitch.accent()))
                        .as(theme.getName()).isGreaterThanOrEqualTo(3.0);
                assertThat(panel.stepByStepButton().getForeground())
                        .isEqualTo(UIManager.getColor("Label.foreground"));
                assertThat(ReadableColors.contrast(panel.stepByStepButton().getForeground(),
                        ReadableColors.panelBackground())).as(theme.getName())
                        .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void underTheSwitchALineSaysWhatThePaceDoesAndWhenTheNextStepComes() {
        assertThat(panel.paceHint().getText()).isEqualTo("Cada paso espera «Siguiente»");

        saved.setAiPace(PresentationPace.CONTINUOUS);
        saved.setAiPauseMillis(2000);
        assertThat(panel.paceHint().getText()).isEqualTo("Los pasos se aplican solos, cada 2 s");

        task.stepPaced(java.time.Duration.ofMillis(1500));
        panel.show(task, false);
        assertThat(panel.paceHint().getText()).isEqualTo("Siguiente paso en 1,5 s…");

        task.stepApplied(step("t1", 1, 1, "Encadeno los acarreos"));
        panel.show(task, false);
        assertThat(panel.paceHint().getText()).isEqualTo("Los pasos se aplican solos, cada 2 s");
    }

    @Test
    void hastaFinDeEtapaIsForPasoAPaso() {
        assertThat(panel.toStageEndButton().isEnabled()).isTrue();

        saved.setAiPace(PresentationPace.CONTINUOUS);

        assertThat(panel.toStageEndButton().isEnabled()).isFalse();
    }

    @Test
    void thePauseIsChosenInThePanelsMenu() {
        List<JMenuItem> pauses = panel.pauseItems();
        assertThat(pauses).extracting(JMenuItem::getText)
                .containsExactly("0,3 s", "0,5 s", "1 s", "2 s", "3 s", "5 s");
        assertThat(pauses.get(2).isSelected()).isTrue();

        pauses.get(3).doClick();

        assertThat(saved.aiPauseMillis()).isEqualTo(2000);
        assertThat(pauses.get(3).isSelected()).isTrue();
        assertThat(pauses.get(2).isSelected()).isFalse();
    }

    @Test
    void theSettingsTheAiTouchedAreAFoldedLineWithTheirCount() {
        assertThat(panel.settingsSection().isVisible()).isFalse();

        SettingsOverride.of(windowPreferences).orElseThrow().set(EditorSetting.SHOW_GUIDES, "true");

        assertThat(panel.settingsSection().isVisible()).isTrue();
        assertThat(panel.settingsHeader().getText()).isEqualTo("Ajustes que tocó la IA (1)");
        assertThat(panel.settingsList().isVisible()).isFalse();
        panel.settingsHeader().doClick();
        assertThat(panel.settingsList().isVisible()).isTrue();
        assertThat(panel.settingTexts()).containsExactly("Mostrar guías: sí");
    }

    @Test
    void foldingHidesEverythingButTheHeader() {
        panel.foldButton().doClick();

        assertThat(panel.body().isVisible()).isFalse();
        assertThat(panel.foldButton().getToolTipText()).isEqualTo("Desplegar");

        panel.foldButton().doClick();
        assertThat(panel.body().isVisible()).isTrue();
    }

    @Test
    void theAisWordsAreNeverHtml() {
        TaskInfo html = new TaskInfo("t2", new DocumentId("d1"), "<html><b>x</b>");
        PresentedTask htmlTask = new PresentedTask(html);
        htmlTask.planChanged(List.of(new TaskStage("<html>y", List.of("<html>z"))), 0, 0);
        panel.show(htmlTask, false);

        assertThat(panel.cards().get(0).lines()).allSatisfy(line ->
                assertThat(line.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE));
    }

    @Test
    void everyToneReadsOnEveryThemeAndIsReadAtPaintTime() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            for (LookAndFeel theme : AppThemes.all()) {
                UIManager.setLookAndFeel(theme);
                Color background = ReadableColors.panelBackground();
                for (StageCard.Tone tone : StageCard.Tone.values()) {
                    assertThat(ReadableColors.contrast(tone.color(), background))
                            .as(tone + " on " + theme.getName())
                            .isGreaterThanOrEqualTo(ReadableColors.MINIMUM_TEXT_CONTRAST);
                }
                StageCard.Line grey = panel.cards().get(1).lines().get(3);
                assertThat(grey.getForeground()).as(theme.getName()).isEqualTo(StageCard.Tone.MUTED.color());
            }
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    @Test
    void disposingStopsFollowingThePreferences() {
        int listeners = saved.listenerCount();
        PresentationPanel another = new PresentationPanel(windowPreferences, controls, () -> { });

        another.dispose();

        assertThat(saved.listenerCount()).isEqualTo(listeners);
    }
}
