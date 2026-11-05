package cl.almejo.vsim.core.command;

import java.util.List;
import java.util.Objects;

/**
 * An edit inside a component refused before anything changed (spec §6.2, phase 1: the interface of a definition used
 * inside another one cannot change yet). Carries the bundle key and arguments of the explanation the editor and the AI
 * show; the document, the working copy and the history are as they were.
 */
public final class InsideEditRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String messageKey;
    private final List<String> arguments;

    public InsideEditRefusedException(String messageKey, List<String> arguments) {
        super(Objects.requireNonNull(messageKey, "messageKey") + " " + arguments);
        this.messageKey = messageKey;
        this.arguments = List.copyOf(arguments);
    }

    /** The bundle key of the explanation, e.g. {@code inside.interfaceLocked}. */
    public String messageKey() {
        return messageKey;
    }

    /** The explanation's arguments, in order ({@code {0}}, {@code {1}}, …). */
    public List<String> arguments() {
        return arguments;
    }
}
