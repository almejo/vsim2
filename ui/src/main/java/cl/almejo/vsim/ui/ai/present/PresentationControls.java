package cl.almejo.vsim.ui.ai.present;

/**
 * What the «Presentar» panel asks of the AI's task (spec §5): implemented over the automation API, called on the
 * editor thread and never waiting there.
 */
public interface PresentationControls {

    /** «Siguiente ▸»: applies the pending step. */
    void next(String taskId);

    /** «Hasta fin de etapa»: applies the rest of the current stage at the «De corrido» pace. */
    void toStageEnd(String taskId);

    /** «Detener»: ends the task like IA.1's «Detener». */
    void stop(String taskId);

    /** «Deshacer etapa»: undoes the stage's steps while they are on top of the history. */
    void undoStage(String taskId, int stageIndex);

    /** «Dejar solo el resultado»: keeps only the last stage's frame and the components created. */
    void keepOnlyResult(String taskId);
}
