package cl.almejo.vsim.core.command;

import java.util.List;
import java.util.Objects;

/**
 * An edit inside a component refused while it applies, before anything changed (spec §6.2: never silently). Carries
 * the bundle key and arguments of the explanation the editor (the component tab's place bar and status line) and the
 * AI show; the document, the working copy and the history are as they were. An interface change is never refused
 * (the bodies that contain the definition follow, spec §6.2 c); what is refused is a body that would hold the
 * definition itself or a piece that contains it (a paste, a dropped file, the AI), and a change of another
 * definition through the working copy, which would never reach the document ({@link EditInsideCommand}).
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

    /** The bundle key of the explanation, e.g. {@code inside.cannotNestItself}. */
    public String messageKey() {
        return messageKey;
    }

    /** The explanation's arguments, in order ({@code {0}}, {@code {1}}, …). */
    public List<String> arguments() {
        return arguments;
    }
}
