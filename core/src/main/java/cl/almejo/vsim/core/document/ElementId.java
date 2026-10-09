package cl.almejo.vsim.core.document;

public record ElementId(String value) {
    public ElementId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("element id must not be blank");
        }
        // "/" separates hierarchical simulation ids (InstancePath); a document id must never look like one.
        if (value.contains("/")) {
            throw new IllegalArgumentException("element id must not contain '/': " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
