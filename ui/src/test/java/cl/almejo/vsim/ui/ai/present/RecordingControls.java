package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.StageUndoOutcome;
import cl.almejo.vsim.automation.present.QuestionAnswer;
import cl.almejo.vsim.automation.present.StoryboardState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controls that only write down what the panel asked for, as «next t1», «undoStage t1», and answer at once with what
 * a test sets: the outcome of «Deshacer etapa», whether «Dejar solo el resultado» is done, the storyboard's state.
 */
final class RecordingControls implements PresentationControls {

    final List<String> calls = new ArrayList<>();
    /** The storyboard's state questions, apart from the buttons' calls. */
    final List<String> stateQuestions = new ArrayList<>();
    StageUndoOutcome nextUndo = new StageUndoOutcome(1, 2, StageUndoOutcome.StoppedAt.NOTHING, true);
    boolean keepSucceeds = true;
    StoryboardState state = new StoryboardState(true, true);

    @Override
    public void next(String taskId) {
        calls.add("next " + taskId);
    }

    @Override
    public void toStageEnd(String taskId) {
        calls.add("toStageEnd " + taskId);
    }

    @Override
    public void stop(String taskId) {
        calls.add("stop " + taskId);
    }

    @Override
    public void undoStage(String taskId, Consumer<StageUndoOutcome> done) {
        calls.add("undoStage " + taskId);
        done.accept(nextUndo);
    }

    @Override
    public void keepOnlyResult(String taskId, Consumer<Boolean> done) {
        calls.add("keepOnlyResult " + taskId);
        done.accept(keepSucceeds);
    }

    @Override
    public void answer(String questionId, QuestionAnswer answer) {
        calls.add("answer " + questionId + answer.choice().map(choice -> " choice " + choice).orElse("")
                + answer.text().map(text -> " text " + text).orElse(""));
    }

    @Override
    public void storyboardState(String taskId, Consumer<StoryboardState> done) {
        stateQuestions.add(taskId);
        done.accept(state);
    }
}
