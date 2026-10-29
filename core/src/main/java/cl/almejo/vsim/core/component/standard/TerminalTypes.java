package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalGeometry;

import java.util.List;
import java.util.Map;

/** The «Terminal» part: a text screen. This first version only has its look, size and pins. */
public final class TerminalTypes {

    /** «Filas». */
    public static final String ROWS = "rows";
    /** «Columnas». */
    public static final String COLUMNS = "columns";
    public static final int DEFAULT_ROWS = 24;
    public static final int DEFAULT_COLUMNS = 80;
    public static final int MAXIMUM_ROWS = 60;
    public static final int MAXIMUM_COLUMNS = 160;

    private static final ParameterSpec ROWS_PARAMETER = ParameterSpec.integer(ROWS, DEFAULT_ROWS, 1, MAXIMUM_ROWS);
    private static final ParameterSpec COLUMNS_PARAMETER = ParameterSpec.integer(COLUMNS, DEFAULT_COLUMNS, 1,
            MAXIMUM_COLUMNS);

    private TerminalTypes() {
    }

    static void register(ComponentTypeRegistry registry) {
        registry.register(ComponentType.parameterized(StandardComponentTypes.TERMINAL, ComponentCategory.INPUT_OUTPUT,
                List.of(ROWS_PARAMETER, COLUMNS_PARAMETER), TerminalTypes::shape, resolved -> new TerminalBehavior()));
    }

    private static ComponentShape shape(Map<String, Object> resolved) {
        return TerminalPinout.shape(TerminalGeometry.size((Integer) resolved.get(ROWS),
                (Integer) resolved.get(COLUMNS), TerminalFontSize.NORMAL));
    }
}
