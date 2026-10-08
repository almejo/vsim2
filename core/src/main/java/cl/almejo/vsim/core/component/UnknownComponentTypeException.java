package cl.almejo.vsim.core.component;

public class UnknownComponentTypeException extends RuntimeException {

    private final String typeId;

    public UnknownComponentTypeException(String typeId) {
        super("unknown component type: " + typeId);
        this.typeId = typeId;
    }

    public String typeId() {
        return typeId;
    }
}
