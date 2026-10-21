package cl.almejo.vsim.automation;

/** What the user answered to a preference proposal. */
public enum ProposalAnswer {
    /** «Aceptar»: the preference was changed. */
    ACCEPTED,
    /** «No», or the proposal was withdrawn (the AI was turned off): nothing changed. */
    DECLINED
}
