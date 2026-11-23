package cl.almejo.vsim.core.component.appearance;

import java.util.List;

/** A strict symbol-path syntax error, with a message key for UI adapters. */
public final class SymbolPathException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final List<Object> arguments;

    public SymbolPathException(String messageKey, Object... arguments) {
        super(messageKey + ": " + List.of(arguments));
        this.arguments = List.of(arguments);
    }

    public List<Object> arguments() {
        return arguments;
    }
}
