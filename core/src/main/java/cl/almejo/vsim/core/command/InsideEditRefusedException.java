package cl.almejo.vsim.core.command;

import java.util.List;
import java.util.Objects;

/**
 * An edit inside a component refused while it applies, before anything changed (spec §6.2: never silently). Carries
 * the bundle key and arguments of the explanation the editor (the component tab's place bar and status line) and the
 * AI show; the document, the working copy and the history are as they were. No edit of the editor is refused today:
 * the interface of a definition used inside another one changes too, and the bodies that contain it follow (spec
 * §6.2 c); the path stays for a rule that needs it.
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
