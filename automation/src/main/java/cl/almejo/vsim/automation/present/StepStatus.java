package cl.almejo.vsim.automation.present;

/** What became of a step in «Presentar» (spec §5.1). */
public enum StepStatus {
    /** The step is in the circuit (one undo step). */
    APPLIED,
    /** The step is shown as a ghost and waits for «Siguiente»; nothing changed in the circuit. */
    PROPOSED,
    /** The user has not pressed «Siguiente» yet: ask again. */
    STILL_WAITING,
    /** The circuit changed while the ghost waited: the step was discarded; read again and propose it again. */
    STALE,
    /**
     * The user pressed «Detener», closed the window, turned the AI off or the task ended: the step was dropped. (Leaving
     * «Presentar» or switching to «De corrido» applies a waiting step instead: a mode change never loses a step.)
     */
    STOPPED
}
