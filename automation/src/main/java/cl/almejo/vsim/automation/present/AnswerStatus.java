package cl.almejo.vsim.automation.present;

/** What {@code vsim_await_answer} says of a question (IA.3). */
public enum AnswerStatus {
    /** The user answered in VSim: the answer comes with it. */
    ANSWERED,
    /** The user has not answered yet: ask again. */
    STILL_WAITING,
    /**
     * The question is gone without an answer: the AI withdrew it (the user answered in the chat), the task stopped or
     * ended, the window closed or the AI was turned off.
     */
    WITHDRAWN
}
