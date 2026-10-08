package cl.almejo.vsim.core.io;

public class CircuitFormatException extends Exception {

    public enum Reason {
        INVALID_JSON, UNSUPPORTED_VERSION, UNKNOWN_COMPONENT_TYPE, INVALID_ELEMENT, READ_ERROR
    }

    private final Reason reason;
    private final String detail;

    public CircuitFormatException(Reason reason, String detail) {
        this(reason, detail, null);
    }

    public CircuitFormatException(Reason reason, String detail, Throwable cause) {
        super(reason + ": " + detail, cause);
        this.reason = reason;
        this.detail = detail;
    }

    public Reason reason() {
        return reason;
    }

    public String detail() {
        return detail;
    }
}
