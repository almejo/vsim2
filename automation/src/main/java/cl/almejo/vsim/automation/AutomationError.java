package cl.almejo.vsim.automation;

/** Why an AI request failed; the MCP layer passes it as the error code of the tool result (spec §10, A§5.2). */
public enum AutomationError {
    /** The edit was prepared against an older version of the circuit; read it again and adapt. */
    STALE_DOCUMENT,
    /** The circuit was closed: its id no longer names anything. */
    DOCUMENT_CLOSED,
    /** An element, alias or document the request names does not exist. */
    INVALID_REFERENCE,
    /** A pin name or index the part does not have, or of the wrong width. */
    INVALID_PIN,
    /** A parameter the type does not have, or a value outside its range. */
    INVALID_PARAMETER,
    /** A wire route that would join nets it was not asked to join, or that cannot be drawn. */
    ROUTE_REJECTED,
    /** An input value the part does not accept. */
    INPUT_REJECTED,
    /** The simulation is busy with another request. */
    SIMULATION_BUSY,
    /** The simulation used its whole event budget; the reached time and state come with the error. */
    BUDGET_EXCEEDED,
    /** Something the AI may not do (save, open files, change preferences without a proposal). */
    NOT_ALLOWED,
    /** The same operation id came back with other arguments. */
    OPERATION_CONFLICT,
    /** The editor did not answer in time: the request may or may not have been applied; read before retrying. */
    OUTCOME_UNKNOWN
}
