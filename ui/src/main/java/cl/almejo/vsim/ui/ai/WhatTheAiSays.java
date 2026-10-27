package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.automation.present.Narration;
import cl.almejo.vsim.automation.present.PresentationListener;
import cl.almejo.vsim.automation.present.Question;
import cl.almejo.vsim.automation.present.StepPreview;
import cl.almejo.vsim.automation.present.StepStatus;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the AI says and asks (IA.3), moved to the EDT for the status lines; the rest of «Presentar» is the
 * panel's.
 */
final class WhatTheAiSays implements PresentationListener {

    private final Consumer<Consumer<AiServerService.Listener>> onEditor;

    /** @param onEditor tells every listener on the EDT */
    WhatTheAiSays(Consumer<Consumer<AiServerService.Listener>> onEditor) {
        this.onEditor = onEditor;
    }

    @Override
    public void planChanged(TaskInfo task, List<TaskStage> stages, int currentStage, int currentStep) {
    }

    @Override
    public void stepProposed(StepPreview preview) {
    }

    @Override
    public void stepApplied(StepPreview preview) {
    }

    @Override
    public void stepDiscarded(String taskId, StepStatus why) {
    }

    @Override
    public void stageChecked(String taskId, int stageIndex, boolean passed, String summary) {
    }

    @Override
    public void narrated(Narration narration) {
        onEditor.accept(listener -> listener.narrated(narration));
    }

    @Override
    public void questionAsked(Question question) {
        onEditor.accept(listener -> listener.questionAsked(question));
    }

    @Override
    public void questionClosed(String taskId, String questionId, boolean answered) {
        onEditor.accept(listener -> listener.questionClosed(taskId, questionId, answered));
    }

    @Override
    public void questionUnattended(String taskId, String questionId, Duration silentFor) {
        onEditor.accept(listener -> listener.questionUnattended(taskId, questionId, silentFor));
    }
}
