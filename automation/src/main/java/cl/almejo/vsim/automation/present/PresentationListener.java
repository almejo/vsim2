package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;

import java.time.Duration;
import java.util.List;

/**
 * Hears a task as «Presentar» shows it (spec §5): its plan and where it is, each step proposed, applied or
 * discarded, and each stage's check. Called off the editor thread.
 */
public interface PresentationListener {

    void planChanged(TaskInfo task, List<TaskStage> stages, int currentStage, int currentStep);

    /** A step waits for «Siguiente» as a ghost («Paso a paso»). */
    void stepProposed(StepPreview preview);

    /** A step is now in the circuit. */
    void stepApplied(StepPreview preview);

    /** The pending step of {@code taskId} was dropped: {@code why} is STALE or STOPPED. */
    void stepDiscarded(String taskId, StepStatus why);

    void stageChecked(String taskId, int stageIndex, boolean passed, String summary);

    /**
     * «De corrido»: the AI's next step of {@code taskId} arrived and is applied once {@code delay} has passed (the
     * pause between steps), so the panel can say «Siguiente paso en …». Nothing by default.
     */
    default void stepPaced(String taskId, Duration delay) {
    }

    /**
     * The AI said {@code narration} (IA.3): what comes and why. In «De corrido» it arrives paced like a short step.
     * Nothing by default.
     */
    default void narrated(Narration narration) {
    }

    /**
     * The AI asked the user {@code question} in VSim (IA.3): the panel or the status strip shows it. Nothing by
     * default.
     */
    default void questionAsked(Question question) {
    }

    /**
     * Question {@code questionId} of task {@code taskId} is gone: the user {@code answered} it in VSim, or it was
     * withdrawn. Nothing by default.
     */
    default void questionClosed(String taskId, String questionId, boolean answered) {
    }
}
