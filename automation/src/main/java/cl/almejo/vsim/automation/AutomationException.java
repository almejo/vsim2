package cl.almejo.vsim.automation;

import java.util.Map;
import java.util.Objects;

/**
 * A request of the AI that VSim refused, with what to do next in {@code recovery} (for example the current version
 * after {@link AutomationError#STALE_DOCUMENT}). The message is English and for the AI, never shown to the user.
 */
public final class AutomationException extends RuntimeException {

    private final AutomationError error;
    private final transient Map<String, Object> recovery;

    public AutomationException(AutomationError error, String message) {
        this(error, message, Map.of());
    }

    public AutomationException(AutomationError error, String message, Map<String, Object> recovery) {
        super(message);
        this.error = Objects.requireNonNull(error, "error");
        this.recovery = Map.copyOf(recovery);
    }

    public AutomationError error() {
        return error;
    }

    public Map<String, Object> recovery() {
        return recovery;
    }
}
