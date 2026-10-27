package cl.almejo.vsim.core.memory;

import java.util.List;

/** A memory file that cannot be read; carries the line, a message key and its arguments for the UI to translate. */
public final class MemoryFileProblem extends Exception {

    private final int line;
    private final String messageKey;
    private final List<Object> arguments;

    public MemoryFileProblem(int line, String messageKey, List<Object> arguments) {
        super("line " + line + ": " + messageKey + " " + arguments);
        this.line = line;
        this.messageKey = messageKey;
        this.arguments = List.copyOf(arguments);
    }

    /** 1-based line of the problem. */
    public int line() {
        return line;
    }

    public String messageKey() {
        return messageKey;
    }

    public List<Object> arguments() {
        return arguments;
    }
}
