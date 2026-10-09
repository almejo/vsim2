package cl.almejo.vsim.core.document;

@FunctionalInterface
public interface DocumentListener {
    /** Called once per change, or once per outermost batch, and only when {@code change} is not empty. */
    void documentChanged(CircuitDocument document, DocumentChange change);
}
