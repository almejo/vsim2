package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.Question;
import cl.almejo.vsim.automation.present.QuestionAnswer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** The AI's tasks without an automation layer: a test fires the events and reads what the editor asked for. */
final class FakeAiTasks implements AiTasks {

    final List<AutomationListener> listeners = new CopyOnWriteArrayList<>();
    final List<PresentationListener> presentationListeners = new CopyOnWriteArrayList<>();
    final List<String> stopped = new ArrayList<>();
    final List<String> undone = new ArrayList<>();
    final List<String> answered = new ArrayList<>();
    UndoOutcome nextOutcome = new UndoOutcome(0, false);

    @Override
    public Runnable addListener(AutomationListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public Runnable addPresentationListener(PresentationListener listener) {
        presentationListeners.add(listener);
        return () -> presentationListeners.remove(listener);
    }

    @Override
    public void stopTask(String taskId) {
        stopped.add(taskId);
    }

    @Override
    public boolean answerQuestion(String questionId, QuestionAnswer answer) {
        answered.add(questionId + answer.choice().map(choice -> " choice " + choice).orElse("")
                + answer.text().map(text -> " text " + text).orElse(""));
        return true;
    }

    void ask(Question question) {
        presentationListeners.forEach(listener -> listener.questionAsked(question));
    }

    void unattended(String taskId, String questionId, java.time.Duration silentFor) {
        presentationListeners.forEach(listener -> listener.questionUnattended(taskId, questionId, silentFor));
    }

    void closeQuestion(String taskId, String questionId, boolean answer) {
        presentationListeners.forEach(listener -> listener.questionClosed(taskId, questionId, answer));
    }

    @Override
    public UndoOutcome undoTask(String taskId) {
        undone.add(taskId);
        return nextOutcome;
    }

    static TaskInfo task(String taskId, DocumentId document, String title) {
        return new TaskInfo(taskId, document, title);
    }

    void narrate(Narration narration) {
        presentationListeners.forEach(listener -> listener.narrated(narration));
    }

    void start(TaskInfo task) {
        listeners.forEach(listener -> listener.taskStarted(task));
    }

    void end(TaskInfo task, Set<String> changedElementIds) {
        listeners.forEach(listener -> listener.taskEnded(task, changedElementIds));
    }

    void stop(TaskInfo task) {
        listeners.forEach(listener -> listener.taskStopped(task));
    }

    void stop(TaskInfo task, cl.almejo.vsim.automation.TaskStopReason reason) {
        listeners.forEach(listener -> listener.taskStopped(task, reason));
    }
}
