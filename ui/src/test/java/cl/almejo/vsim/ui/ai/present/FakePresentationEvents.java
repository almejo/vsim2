package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** The presentation stream without an automation layer: a test fires the events as the pacing layer would. */
final class FakePresentationEvents implements PresentationEvents {

    final List<PresentationListener> presentationListeners = new CopyOnWriteArrayList<>();
    final List<AutomationListener> taskListeners = new CopyOnWriteArrayList<>();

    @Override
    public Runnable addPresentationListener(PresentationListener listener) {
        presentationListeners.add(listener);
        return () -> presentationListeners.remove(listener);
    }

    @Override
    public Runnable addTaskListener(AutomationListener listener) {
        taskListeners.add(listener);
        return () -> taskListeners.remove(listener);
    }

    void start(TaskInfo task) {
        taskListeners.forEach(listener -> listener.taskStarted(task));
    }

    void plan(TaskInfo task, List<TaskStage> stages, int currentStage, int currentStep) {
        presentationListeners.forEach(listener -> listener.planChanged(task, stages, currentStage, currentStep));
    }

    void propose(StepPreview preview) {
        presentationListeners.forEach(listener -> listener.stepProposed(preview));
    }

    void apply(StepPreview preview) {
        presentationListeners.forEach(listener -> listener.stepApplied(preview));
    }

    void discard(String taskId, StepStatus why) {
        presentationListeners.forEach(listener -> listener.stepDiscarded(taskId, why));
    }

    void check(String taskId, int stageIndex, boolean passed, String summary) {
        presentationListeners.forEach(listener -> listener.stageChecked(taskId, stageIndex, passed, summary));
    }

    void narrate(Narration narration) {
        presentationListeners.forEach(listener -> listener.narrated(narration));
    }

    void end(TaskInfo task) {
        taskListeners.forEach(listener -> listener.taskEnded(task, Set.of()));
    }

    void stop(TaskInfo task) {
        taskListeners.forEach(listener -> listener.taskStopped(task));
    }

    static StepPreview step(String taskId, int stageIndex, int stepIndex, String text) {
        return new StepPreview(taskId, stageIndex, stepIndex, text, Optional.empty(), List.of(), Set.of(),
                Optional.empty());
    }
}
