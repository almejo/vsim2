package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.automation.StageUndoOutcome;
import cl.almejo.vsim.automation.present.QuestionAnswer;
import cl.almejo.vsim.automation.present.StoryboardState;

import java.util.function.Consumer;

/**
 * What the «Presentar» panel asks of the AI's task (spec §5): implemented over the automation API, called on the
 * editor thread and never waiting there; the answers come back on the editor thread.
 */
public interface PresentationControls {

    /** «Siguiente ▸»: applies the pending step. */
    void next(String taskId);

    /** «Hasta fin de etapa»: applies the rest of the current stage at the «De corrido» pace. */
    void toStageEnd(String taskId);

    /** «Detener»: ends the task like IA.1's «Detener». */
    void stop(String taskId);

    /**
     * «Deshacer etapa»: undoes the latest stage that still has steps in the circuit, while they are on top of the
     * history; {@code done} hears what it did.
     */
    void undoStage(String taskId, Consumer<StageUndoOutcome> done);

    /**
     * «Dejar solo el resultado»: keeps only the last stage's frame and the components created; {@code done} hears
     * true when it did, false when it was refused (the stages are not a storyboard, a step still waits).
     */
    void keepOnlyResult(String taskId, Consumer<Boolean> done);

    /** What the panel may offer for the task's storyboard now («Deshacer etapa», «Dejar solo el resultado»). */
    void storyboardState(String taskId, Consumer<StoryboardState> done);

    /** The user answered the AI's question in the panel (IA.3); VSim closes it. Nothing by default. */
    default void answer(String questionId, QuestionAnswer answer) {
    }
}
