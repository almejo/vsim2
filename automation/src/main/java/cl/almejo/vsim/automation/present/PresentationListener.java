package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;

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
}
