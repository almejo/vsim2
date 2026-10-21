package cl.almejo.vsim.core.io;

public class CircuitFormatException extends Exception {

    public enum Reason {
        INVALID_JSON, UNSUPPORTED_VERSION, UNKNOWN_COMPONENT_TYPE, INVALID_ELEMENT, READ_ERROR,
        /** A VSim 2 file of a newer {@code formatVersion} than this build reads; the detail is that version. */
        NEWER_FORMAT,
        /** JSON that is not a VSim file: no {@code "format": "vsim2"} marker and no {@code formatVersion}. */
        NOT_VSIM_FILE
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
