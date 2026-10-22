package cl.almejo.vsim.automation.present;

/** How «Presentar» shows the AI's steps (spec §5): one after another with a pause, or each waiting for «Siguiente». */
public enum PresentationPace {
    CONTINUOUS,
    STEP_BY_STEP
}
