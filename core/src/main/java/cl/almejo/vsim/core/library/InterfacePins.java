package cl.almejo.vsim.core.library;

/** The components that mark where a template's pins connect to its body. */
public final class InterfacePins {

    public static final String INPUT_PIN = "input-pin";
    public static final String OUTPUT_PIN = "output-pin";

    private InterfacePins() {
    }

    public static boolean isInterface(String typeId) {
        return INPUT_PIN.equals(typeId) || OUTPUT_PIN.equals(typeId);
    }
}
