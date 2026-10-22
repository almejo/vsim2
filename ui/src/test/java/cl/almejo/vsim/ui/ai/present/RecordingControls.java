package cl.almejo.vsim.ui.ai.present;

import java.util.ArrayList;
import java.util.List;

/** Controls that only write down what the panel asked for, as «next t1», «undoStage t1 0». */
final class RecordingControls implements PresentationControls {

    final List<String> calls = new ArrayList<>();

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
    public void undoStage(String taskId, int stageIndex) {
        calls.add("undoStage " + taskId + " " + stageIndex);
    }

    @Override
    public void keepOnlyResult(String taskId) {
        calls.add("keepOnlyResult " + taskId);
    }
}
