package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One AI task as the «Presentar» panel follows it (spec §5): its plan, the stage and the step it is on (the first
 * step of the current stage not applied yet), the step waiting for «Siguiente», each stage's check, the last discard
 * and whether it ended. The panel only reads it. EDT only.
 */
final class PresentedTask {

    enum StageState { DONE, CURRENT, FUTURE }

    enum StepState { DONE, CURRENT, NEXT }

    /** What VSim measured at a stage's end («✓ cumple la tabla»). */
    record StageCheck(boolean passed, String summary) {

        StageCheck {
            Objects.requireNonNull(summary, "summary");
        }
    }

    private final TaskInfo task;
    private final Map<Integer, StageCheck> checks = new HashMap<>();
    private List<TaskStage> stages = List.of();
    private int currentStage;
    private int currentStep;
    private StepPreview pending;
    private StepStatus lastDiscard;
    /** «De corrido»: how long the step on its way waits for its pause; null when none waits. */
    private Duration pacedFor;
    private boolean ended;

    PresentedTask(TaskInfo task) {
        this.task = Objects.requireNonNull(task, "task");
    }

    TaskInfo task() {
        return task;
    }

    void planChanged(List<TaskStage> newStages, int stage, int step) {
        stages = List.copyOf(newStages);
        currentStage = stage;
        currentStep = step;
    }

    void stepProposed(StepPreview preview) {
        pacedFor = null;
        pending = preview;
        lastDiscard = null;
        currentStage = preview.stageIndex();
        currentStep = preview.stepIndex();
    }

    void stepApplied(StepPreview preview) {
        pacedFor = null;
        pending = null;
        lastDiscard = null;
        currentStage = preview.stageIndex();
        currentStep = preview.stepIndex() + 1;
    }

    /** The pending step was dropped: {@code why} is {@link StepStatus#STALE} or {@link StepStatus#STOPPED}. */
    /** «De corrido»: the next step is applied once {@code delay} has passed. */
    void stepPaced(Duration delay) {
        pacedFor = Objects.requireNonNull(delay, "delay");
    }

    /** How long the step on its way waits for its pause, until it is applied. */
    Optional<Duration> pacedFor() {
        return Optional.ofNullable(pacedFor);
    }

    void stepDiscarded(StepStatus why) {
        pacedFor = null;
        pending = null;
        lastDiscard = why;
    }

    void stageChecked(int stageIndex, boolean passed, String summary) {
        checks.put(stageIndex, new StageCheck(passed, summary));
    }

    void ended() {
        ended = true;
        pacedFor = null;
        pending = null;
    }

    boolean isEnded() {
        return ended;
    }

    List<TaskStage> stages() {
        return stages;
    }

    int currentStage() {
        return currentStage;
    }

    StageState stageState(int stageIndex) {
        if (ended || stageIndex < currentStage) {
            return StageState.DONE;
        }
        return stageIndex == currentStage ? StageState.CURRENT : StageState.FUTURE;
    }

    /** Within the current stage; the steps of a done stage are all done, those of a future one all next. */
    StepState stepState(int stageIndex, int stepIndex) {
        return switch (stageState(stageIndex)) {
            case DONE -> StepState.DONE;
            case FUTURE -> StepState.NEXT;
            case CURRENT -> stepIndex < currentStep ? StepState.DONE
                    : stepIndex == currentStep ? StepState.CURRENT : StepState.NEXT;
        };
    }

    Optional<StageCheck> check(int stageIndex) {
        return Optional.ofNullable(checks.get(stageIndex));
    }

    Optional<StepPreview> pending() {
        return Optional.ofNullable(pending);
    }

    /** Why the last pending step was dropped, until the next step comes. */
    Optional<StepStatus> lastDiscard() {
        return Optional.ofNullable(lastDiscard);
    }

    /**
     * The stage «Deshacer etapa» undoes: the current one once one of its steps is in, else the one before; the last
     * one when the task ended.
     */
    OptionalInt stageToUndo() {
        if (ended) {
            return stages.isEmpty() ? OptionalInt.empty() : OptionalInt.of(stages.size() - 1);
        }
        if (currentStep > 0) {
            return OptionalInt.of(currentStage);
        }
        return currentStage > 0 ? OptionalInt.of(currentStage - 1) : OptionalInt.empty();
    }
}
