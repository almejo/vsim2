package cl.almejo.vsim.automation;

/** Why a task stopped before its end (IA.3): the user's «Detener», or VSim because the AI stopped answering. */
public enum TaskStopReason {
    /** «Detener» (or the panel's Apagar / Reiniciar): the user stopped it. */
    USER,
    /** A step waited for «Siguiente» and the AI asked nothing about it for {@code SILENCE_LIMIT}: the client is gone. */
    AI_SILENT
}
