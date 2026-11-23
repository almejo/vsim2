package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.ExpressionProblem;
import cl.almejo.vsim.ui.i18n.Messages;

/**
 * How a problem of the notation of «Circuito desde expresiones» reads for the user, wherever the notation is typed
 * (the assistant's lines, a state machine's conditions).
 */
public final class ExpressionProblemText {

    private ExpressionProblemText() {
    }

    /** A problem's message with its position when it has one: «Falta cerrar un paréntesis (posición 9)». */
    public static String describe(ExpressionProblem problem) {
        String message = Messages.format(problem.messageKey(), problem.arguments().toArray());
        if (problem.column() < 0) {
            return message;
        }
        String bare = message.endsWith(".") ? message.substring(0, message.length() - 1) : message;
        return Messages.format("expressions.position", bare, String.valueOf(problem.column() + 1));
    }
}
