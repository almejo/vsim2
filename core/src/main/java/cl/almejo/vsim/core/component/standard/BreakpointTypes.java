package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.Map;

/**
 * The «Punto de parada» (Depuración): one input of «Ancho (bits)» bits that pauses the simulation, like the Terminal's
 * «Detener al recibir», each time its «Condición» (igual a / distinto de «Valor») becomes true; «Activo» switches it
 * off without removing it.
 */
public final class BreakpointTypes {

    public static final String BREAKPOINT = "breakpoint";

    public static final String CONDITION = "condition";
    public static final String EQUALS = "equals";
    public static final String DIFFERS = "differs";
    public static final String ACTIVE = "active";

    /** The pause notice keys; the arguments are the value read, and for {@code differs} also the value it differs from. */
    public static final String STOPPED_EQUALS_MESSAGE = "breakpoint.stopped.equals";
    public static final String STOPPED_DIFFERS_MESSAGE = "breakpoint.stopped.differs";

    private static final int DEFAULT_WIDTH = 8;
    private static final int MINIMUM_WIDTH = 1;
    private static final int MAXIMUM_WIDTH = Value.MAXIMUM_WIDTH;
    private static final GridSize SIZE = new GridSize(8, 2);
    private static final int PIN_ROW = 1;

    private BreakpointTypes() {
    }

    static void register(ComponentTypeRegistry registry) {
        registry.register(ComponentType.parameterized(BREAKPOINT, ComponentCategory.DEBUG,
                List.of(ParameterSpec.integer(StandardComponentTypes.WIDTH, DEFAULT_WIDTH, MINIMUM_WIDTH, MAXIMUM_WIDTH),
                        ParameterSpec.choice(CONDITION, EQUALS, List.of(EQUALS, DIFFERS)),
                        ParameterSpec.text(StandardComponentTypes.VALUE, "0", ConstantValue::normalize),
                        ParameterSpec.bool(ACTIVE, true)),
                parameters -> new ComponentShape(SIZE, List.of(PinSpec.input("in", 0, PIN_ROW,
                        (Integer) parameters.get(StandardComponentTypes.WIDTH)))),
                raw -> StandardComponentTypes.checkValueFitsWidth(raw, DEFAULT_WIDTH, MAXIMUM_WIDTH),
                parameters -> BreakpointBehavior.from(parameters)));
    }

    /** The condition's choice as a boolean: {@code true} for «igual a». */
    static boolean isEquals(Map<String, Object> parameters) {
        return EQUALS.equals(parameters.get(CONDITION));
    }
}
