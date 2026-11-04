package cl.almejo.vsim.core.io;

public class CircuitFormatException extends Exception {

    public enum Reason {
        INVALID_JSON, UNSUPPORTED_VERSION, UNKNOWN_COMPONENT_TYPE, INVALID_ELEMENT, READ_ERROR,
        /** A VSim 2 file of a newer {@code formatVersion} than this build reads; the detail is that version. */
        NEWER_FORMAT,
        /** JSON that is not a VSim file: no {@code "format": "vsim2"} marker and no {@code formatVersion}. */
        NOT_VSIM_FILE,
        /** A file to import as XML (a Logisim {@code .circ}) whose text is not XML at all. */
        NOT_XML,
        /** XML whose root is not a Logisim {@code <project>}. */
        NOT_LOGISIM_PROJECT
    }

    private final Reason reason;
    private final String detail;
    private final Element element;
    private final String savedWith;

    public CircuitFormatException(Reason reason, String detail) {
        this(reason, detail, null);
    }

    public CircuitFormatException(Reason reason, String detail, Throwable cause) {
        this(reason, detail, cause, null, null);
    }

    private CircuitFormatException(Reason reason, String detail, Throwable cause, Element element, String savedWith) {
        super(reason + ": " + detail, cause);
        this.reason = reason;
        this.detail = detail;
        this.element = element;
        this.savedWith = savedWith;
    }

    /** A {@code NEWER_FORMAT} failure: {@code formatVersion} is the file's, {@code savedWith} the app version (or null). */
    static CircuitFormatException newer(String formatVersion, String savedWith) {
        return new CircuitFormatException(Reason.NEWER_FORMAT, formatVersion, null, null, savedWith);
    }

    /** The same failure, saying which element of the file it is about. */
    CircuitFormatException atElement(String kind, String id, String type) {
        return new CircuitFormatException(reason, detail, getCause(), new Element(kind, id, type), savedWith);
    }

    /**
     * The element of the file a failure is about: {@code kind} is the file's key ({@code components}, {@code wires},
     * {@code labels}, {@code frames}, {@code stickyNotes}), {@code id} the element's, {@code type} its component type
     * id (null for anything but a component).
     */
    public record Element(String kind, String id, String type) {
    }

    /** An {@code INVALID_ELEMENT} failure: a well-formed file with an element the format does not allow. */
    public static CircuitFormatException invalid(String detail) {
        return new CircuitFormatException(Reason.INVALID_ELEMENT, detail);
    }

    public static CircuitFormatException invalid(String detail, Throwable cause) {
        return new CircuitFormatException(Reason.INVALID_ELEMENT, detail, cause);
    }

    public Reason reason() {
        return reason;
    }

    public String detail() {
        return detail;
    }

    /** Which element of the file failed; null when not known. */
    public Element element() {
        return element;
    }

    /** The VSim version a {@code NEWER_FORMAT} file says it was saved with; null when it says nothing. */
    public String savedWith() {
        return savedWith;
    }
}
