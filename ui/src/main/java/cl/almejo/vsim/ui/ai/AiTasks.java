package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.AutomationListener;
import cl.almejo.vsim.automation.UndoOutcome;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.QuestionAnswer;

import java.util.Objects;

/**
 * The AI's tasks as the editor sees them: their events, «Detener» and «Deshacer lo que hizo la IA». The automation
 * layer implements them ({@link #of}); tests give a fake. {@link #stopTask} and {@link #undoTask} may wait for the
 * editor thread, so the editor calls them off it.
 */
public interface AiTasks {

    /** {@code listener} hears every task begin and end (off the EDT) until the returned runnable takes it back. */
    Runnable addListener(AutomationListener listener);

    /**
     * {@code listener} hears every task as «Presentar» follows it (off the EDT), here for what the AI says (IA.3),
     * until the returned runnable takes it back. Nothing by default.
     */
    default Runnable addPresentationListener(PresentationListener listener) {
        return () -> {
        };
    }

    void stopTask(String taskId);

    UndoOutcome undoTask(String taskId);

    /** The user answered the AI's question (IA.3): false when it is closed or does not take it. False by default. */
    default boolean answerQuestion(String questionId, QuestionAnswer answer) {
        return false;
    }

    static AiTasks of(AutomationApi api) {
        Objects.requireNonNull(api, "api");
        return new AiTasks() {
            @Override
            public Runnable addListener(AutomationListener listener) {
                return api.addListener(listener);
            }

            @Override
            public Runnable addPresentationListener(PresentationListener listener) {
                return api.addPresentationListener(listener);
            }

            @Override
            public void stopTask(String taskId) {
                api.stopTask(taskId);
            }

            @Override
            public UndoOutcome undoTask(String taskId) {
                return api.undoTask(taskId);
            }

            @Override
            public boolean answerQuestion(String questionId, QuestionAnswer answer) {
                return api.answerQuestion(questionId, answer);
            }
        };
    }
}
