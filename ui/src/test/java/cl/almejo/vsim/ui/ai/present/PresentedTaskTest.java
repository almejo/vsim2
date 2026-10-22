package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.StageUndoOutcome;
import cl.almejo.vsim.automation.StageUndoOutcome.StoppedAt;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.automation.present.StoryboardState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cl.almejo.vsim.ui.ai.present.FakePresentationEvents.step;
import static org.assertj.core.api.Assertions.assertThat;

/** Where a presented task is, as the panel shows it (spec §5): stages done, current and future, and its steps. */
class PresentedTaskTest {

    private static final TaskInfo TASK = new TaskInfo("t1", new DocumentId("d1"), "Sumador de 4 bits");
    private static final List<TaskStage> STAGES = List.of(
            new TaskStage("Sumador de 1 bit", List.of("Pongo las XOR", "Pongo las AND", "Conecto el acarreo")),
            new TaskStage("Sumador de 4 bits", List.of("Pongo cuatro sumadores", "Encadeno los acarreos")),
            new TaskStage("Componente", List.of()));

    private final PresentedTask task = new PresentedTask(TASK);

    @Test
    void beforeThePlanItIsTheTitleAlone() {
        assertThat(task.task()).isEqualTo(TASK);
        assertThat(task.stages()).isEmpty();
        assertThat(task.pending()).isEmpty();
        assertThat(task.canUndoStage()).isFalse();
        assertThat(task.isEnded()).isFalse();
    }

    @Test
    void thePlanSaysWhichStagesAreDoneCurrentAndFutureAndWhichStepsAreDoneCurrentAndNext() {
        task.planChanged(STAGES, 1, 1);

        assertThat(task.stageState(0)).isEqualTo(PresentedTask.StageState.DONE);
        assertThat(task.stageState(1)).isEqualTo(PresentedTask.StageState.CURRENT);
        assertThat(task.stageState(2)).isEqualTo(PresentedTask.StageState.FUTURE);
        assertThat(task.stepState(1, 0)).isEqualTo(PresentedTask.StepState.DONE);
        assertThat(task.stepState(1, 1)).isEqualTo(PresentedTask.StepState.CURRENT);
        assertThat(task.currentStage()).isEqualTo(1);
    }

    @Test
    void aProposedStepWaitsAndOnceAppliedTheNextOneIsCurrent() {
        task.planChanged(STAGES, 0, 0);

        task.stepProposed(step("t1", 0, 1, "Pongo las AND"));

        assertThat(task.pending()).map(preview -> preview.text()).contains("Pongo las AND");
        assertThat(task.stepState(0, 0)).isEqualTo(PresentedTask.StepState.DONE);
        assertThat(task.stepState(0, 1)).isEqualTo(PresentedTask.StepState.CURRENT);
        assertThat(task.stepState(0, 2)).isEqualTo(PresentedTask.StepState.NEXT);

        task.stepApplied(step("t1", 0, 1, "Pongo las AND"));

        assertThat(task.pending()).isEmpty();
        assertThat(task.stepState(0, 1)).isEqualTo(PresentedTask.StepState.DONE);
        assertThat(task.stepState(0, 2)).isEqualTo(PresentedTask.StepState.CURRENT);
    }

    @Test
    void aStaleOrStoppedDiscardDropsThePendingStepAndIsRemembered() {
        task.planChanged(STAGES, 0, 0);
        task.stepProposed(step("t1", 0, 0, "Pongo las XOR"));

        task.stepDiscarded(StepStatus.STALE);

        assertThat(task.pending()).isEmpty();
        assertThat(task.lastDiscard()).contains(StepStatus.STALE);
        task.stepProposed(step("t1", 0, 0, "Pongo las XOR"));
        assertThat(task.lastDiscard()).isEmpty();
    }

    @Test
    void eachStageKeepsItsCheck() {
        task.planChanged(STAGES, 1, 0);

        task.stageChecked(0, true, "cumple la tabla");
        task.stageChecked(1, false, "la fila 3 da 0");

        assertThat(task.check(0)).contains(new PresentedTask.StageCheck(true, "cumple la tabla"));
        assertThat(task.check(1)).contains(new PresentedTask.StageCheck(false, "la fila 3 da 0"));
        assertThat(task.check(2)).isEmpty();
    }
    @Test
    void deshacerEtapaIsOfferedOnceAStepIsInAndUntilVsimSaysNothingIsLeft() {
        task.planChanged(STAGES, 0, 0);
        assertThat(task.canUndoStage()).isFalse();

        task.stepApplied(step("t1", 0, 0, "Pongo las XOR"));
        assertThat(task.canUndoStage()).isTrue();

        task.stageUndone(new StageUndoOutcome(0, 1, StoppedAt.NOTHING, false));
        assertThat(task.canUndoStage()).isFalse();
        assertThat(task.lastUndo()).contains(new StageUndoOutcome(0, 1, StoppedAt.NOTHING, false));

        task.stepApplied(step("t1", 0, 0, "Pongo las XOR"));
        assertThat(task.canUndoStage()).isTrue();
        assertThat(task.lastUndo()).isEmpty();
    }

    @Test
    void anEndedTaskHasEveryStageDoneNothingPendingAndAsksVsimWhatItMayOffer() {
        task.planChanged(STAGES, 2, 0);
        task.stepProposed(step("t1", 2, 0, "Creo el componente"));

        task.ended();

        assertThat(task.isEnded()).isTrue();
        assertThat(task.pending()).isEmpty();
        assertThat(List.of(task.stageState(0), task.stageState(1), task.stageState(2)))
                .containsOnly(PresentedTask.StageState.DONE);
        assertThat(task.canUndoStage()).isTrue();
        assertThat(task.canKeepOnlyResult()).as("until VSim says the stages are separate").isFalse();
        assertThat(task.storyboardKnown()).isFalse();

        task.storyboardChanged(new StoryboardState(false, true));

        assertThat(task.storyboardKnown()).isTrue();
        assertThat(task.canUndoStage()).isFalse();
        assertThat(task.canKeepOnlyResult()).isTrue();
    }

    @Test
    void aStoppedTaskStaysLikeAnEndedOneAndSaysItWasStopped() {
        task.planChanged(STAGES, 1, 1);

        task.stopped();

        assertThat(task.isEnded()).isTrue();
        assertThat(task.isStopped()).isTrue();
        assertThat(task.canUndoStage()).isTrue();
    }
}
