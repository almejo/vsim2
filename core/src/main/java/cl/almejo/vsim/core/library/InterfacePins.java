package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;

/** The components that mark where a template's pins connect to its body. */
public final class InterfacePins {

    public static final String INPUT_PIN = "input-pin";
    public static final String OUTPUT_PIN = "output-pin";

    /** The parameter that sets an interface pin's width in bits. */
    public static final String WIDTH = "width";

    /** The {@link #WIDTH} parameter: 1..64 bits, 1 by default. The one place its range is checked. */
    public static final ParameterSpec WIDTH_PARAMETER = ParameterSpec.integer(WIDTH, 1, 1, Value.MAXIMUM_WIDTH);

    private InterfacePins() {
    }

    public static boolean isInterface(String typeId) {
        return INPUT_PIN.equals(typeId) || OUTPUT_PIN.equals(typeId);
    }

    /** M7: an input pin of more than one bit, whose value can be typed while simulating. */
    public static boolean isBusInput(Component component) {
        return INPUT_PIN.equals(component.typeId()) && widthOf(component) > 1;
    }

    /**
     * The width of an interface pin component: its {@code width} parameter, or 1 when absent (files written before
     * buses). A value outside 1..64 or not a number throws IllegalArgumentException.
     */
    public static int widthOf(Component component) {
        Object raw = component.parameters().get(WIDTH);
        return raw == null ? 1 : (Integer) WIDTH_PARAMETER.normalize(raw);
    }
}
