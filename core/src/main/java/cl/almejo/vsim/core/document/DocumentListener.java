package cl.almejo.vsim.core.document;

@FunctionalInterface
public interface DocumentListener {
    void documentChanged(CircuitDocument document);
}
