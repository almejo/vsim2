package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.StageUndoOutcome;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;
import cl.almejo.vsim.automation.present.StoryboardState;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One AI task as the «Presentar» panel follows it (spec §5): its plan, the stage and the step it is on (the first
 * step of the current stage not applied yet), the step waiting for «Siguiente», each stage's check, the last discard,
 * whether it ended or was stopped, and what VSim said about its storyboard (whether a stage is left to undo, whether
 * only the result can be kept) and the last «Deshacer etapa». The panel only reads it. EDT only.
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
    private boolean stopped;
    private boolean silent;
    /** Whether a step of the task went in (until VSim says otherwise). */
    private boolean anyStepIn;
    /** What VSim said about the storyboard since the last step; null until it said. */
    private StoryboardState storyboard;
    private boolean storyboardAsked;
    private StageUndoOutcome lastUndo;
    private boolean keepRefused;

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
        if (stage > 0 || step > 0) {
            anyStepIn = true;
        }
    }

    void stepProposed(StepPreview preview) {
        pacedFor = null;
        pending = preview;
        lastDiscard = null;
        lastUndo = null;
        keepRefused = false;
        currentStage = preview.stageIndex();
        currentStep = preview.stepIndex();
    }

    void stepApplied(StepPreview preview) {
        pacedFor = null;
        pending = null;
        lastDiscard = null;
        lastUndo = null;
        keepRefused = false;
        anyStepIn = true;
        storyboard = null;
        storyboardAsked = false;
        currentStage = preview.stageIndex();
        currentStep = preview.stepIndex() + 1;
    }

    /** «De corrido»: the next step is applied once {@code delay} has passed. */
    void stepPaced(Duration delay) {
        pacedFor = Objects.requireNonNull(delay, "delay");
    }

    /** How long the step on its way waits for its pause, until it is applied. */
    Optional<Duration> pacedFor() {
        return Optional.ofNullable(pacedFor);
    }

    /** The pending step was dropped: {@code why} is {@link StepStatus#STALE} or {@link StepStatus#STOPPED}. */
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

    /** «Detener»: like the end, the panel stays with «Deshacer etapa» and ×. */
    void stopped() {
        ended();
        stopped = true;
    }

    /** VSim stopped the task because the AI stopped answering while a step waited (IA.3): like «Detener». */
    void stoppedBecauseTheAiWentSilent() {
        stopped();
        silent = true;
    }

    /** True once the task ended or was stopped. */
    boolean isEnded() {
        return ended;
    }

    boolean isStopped() {
        return stopped;
    }

    /** True when VSim, not the user, stopped the task: the AI stopped answering. */
    boolean stoppedBecauseSilent() {
        return silent;
    }

    // --- the storyboard, as VSim says it is ---

    /** VSim said what the panel may offer now. */
    void storyboardChanged(StoryboardState state) {
        storyboard = Objects.requireNonNull(state, "state");
        storyboardAsked = true;
    }

    /** True once VSim said what the panel may offer (or the panel asked it already). */
    boolean storyboardKnown() {
        return storyboard != null;
    }

    /** True once the panel asked VSim, so it asks only once until something changes. */
    boolean storyboardAsked() {
        return storyboardAsked;
    }

    void storyboardAskedNow() {
        storyboardAsked = true;
    }

    /** «Deshacer etapa» did {@code outcome}; VSim is asked again what is left. */
    void stageUndone(StageUndoOutcome outcome) {
        lastUndo = Objects.requireNonNull(outcome, "outcome");
        keepRefused = false;
        storyboard = new StoryboardState(outcome.moreToUndo(), storyboard != null && storyboard.canKeepOnlyResult());
        storyboardAsked = false;
    }

    /** «Dejar solo el resultado» was done ({@code true}) or refused; VSim is asked again what is left. */
    void keptOnlyResult(boolean done) {
        lastUndo = null;
        keepRefused = !done;
        boolean canUndo = storyboard == null || storyboard.canUndoStage();
        storyboard = new StoryboardState(canUndo, false);
        storyboardAsked = false;
    }

    /** What the last «Deshacer etapa» did, until the next step or button. */
    Optional<StageUndoOutcome> lastUndo() {
        return Optional.ofNullable(lastUndo);
    }

    /** True when the last «Dejar solo el resultado» was refused. */
    boolean keepRefused() {
        return keepRefused;
    }

    /** «Deshacer etapa» is on once a step went in, until VSim says no stage has steps left. */
    boolean canUndoStage() {
        return storyboard != null ? storyboard.canUndoStage() : anyStepIn;
    }

    /** «Dejar solo el resultado» is offered only when VSim says the stages are a storyboard. */
    boolean canKeepOnlyResult() {
        return storyboard != null && storyboard.canKeepOnlyResult();
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
}
