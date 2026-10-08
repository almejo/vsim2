package cl.almejo.vsim.core.document;

public record ElementId(String value) {
    public ElementId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("element id must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
