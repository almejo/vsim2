package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The «Matriz LED» part: a grid of one-bit LEDs that draws letters, figures and games. It has two ways in, chosen by
 * «Entrada»: «Por filas» (one input per row, a bit per column) and «Barrido» (a row number, the data for that row and an
 * enable; each row keeps what it last got, like a scanned display). See {@link LedMatrixBehavior}.
 *
 * <p>Pins sit on the left edge, one grid unit apart from the top, so the LEDs of a row line up with its pin: in
 * «Por filas» {@code F0}… at {@code y = 1…rows}; in «Barrido» {@code Fila}, {@code Datos} and {@code Hab.} at
 * {@code y = 1, 3, 5}. The body starts one grid unit in from the left edge (the lead of the pins) and each LED has a
 * grid unit of its own, with half a unit of frame around them.
 */
public final class LedMatrixTypes {

    public static final String ROWS = "rows";
    public static final String COLUMNS = "columns";
    /** «Entrada»: {@link #BY_ROWS} or {@link #SCAN}. */
    public static final String INPUT = "input";
    /** «Aspecto»: one of {@link #STYLES}; a look only. */
    public static final String STYLE = "style";
    public static final String COLOR = "color";
    /** «Ver los apagados». */
    public static final String SHOW_UNLIT = "show-unlit";
    /** «Brillo que se apaga»: a visual fade; the simulation never knows about it. */
    public static final String AFTERGLOW = "afterglow";

    public static final String BY_ROWS = "rows";
    public static final String SCAN = "scan";
    public static final List<String> STYLES = List.of("round", "pixel", "module", "sign");

    public static final int MINIMUM_ROWS = 1;
    public static final int MAXIMUM_ROWS = 32;
    public static final int MINIMUM_COLUMNS = 1;
    /** 64 is also the widest bus of VSim, so one row always fits one pin. */
    public static final int MAXIMUM_COLUMNS = Value.MAXIMUM_WIDTH;
    public static final int DEFAULT_SIZE = 8;

    /** The grid units between the left edge (the pins) and the body. */
    public static final int LEAD_UNITS = 1;
    /**
     * The grid units wider than its LEDs a matrix is: the lead of the pins, plus half a unit of frame on each side of
     * the LEDs.
     */
    public static final int EXTRA_WIDTH_UNITS = 2;
    /** The grid units taller than its LEDs a matrix is: half a unit of frame above and below them. */
    public static final int EXTRA_HEIGHT_UNITS = 1;

    private static final int SCAN_PIN_SPACING = 2;
    private static final int SCAN_PIN_COUNT = 3;
    /** «Barrido» always has room for its three pins, even for a one-row matrix. */
    private static final int MINIMUM_SCAN_HEIGHT = SCAN_PIN_SPACING * SCAN_PIN_COUNT;

    private static final List<ParameterSpec> PARAMETERS = List.of(
            ParameterSpec.integer(ROWS, DEFAULT_SIZE, MINIMUM_ROWS, MAXIMUM_ROWS),
            ParameterSpec.integer(COLUMNS, DEFAULT_SIZE, MINIMUM_COLUMNS, MAXIMUM_COLUMNS),
            ParameterSpec.choice(INPUT, BY_ROWS, List.of(BY_ROWS, SCAN)),
            ParameterSpec.choice(STYLE, STYLES.get(0), STYLES).asDisplayOnly(),
            ParameterSpec.color(COLOR, "red",
                    List.of("red", "green", "yellow", "blue", "orange", "white", "purple", "cyan")).asDisplayOnly(),
            ParameterSpec.bool(SHOW_UNLIT, true).asDisplayOnly(),
            ParameterSpec.bool(AFTERGLOW, false).asDisplayOnly());

    private LedMatrixTypes() {
    }

    static void register(ComponentTypeRegistry registry) {
        registry.register(ComponentType.parameterized(StandardComponentTypes.LED_MATRIX,
                ComponentCategory.INPUT_OUTPUT, PARAMETERS, LedMatrixTypes::shape,
                parameters -> new LedMatrixBehavior((Integer) parameters.get(ROWS),
                        (Integer) parameters.get(COLUMNS), isScan(parameters))));
    }

    /** Whether {@code parameters} ask for «Barrido». */
    public static boolean isScan(Map<String, Object> parameters) {
        return SCAN.equals(parameters.get(INPUT));
    }

    /** The bits of the «Fila» input of a scanned matrix of {@code rows} rows: enough for the last row, at least one. */
    public static int rowBits(int rows) {
        return Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(rows - 1));
    }

    private static ComponentShape shape(Map<String, Object> parameters) {
        int rows = (Integer) parameters.get(ROWS);
        int columns = (Integer) parameters.get(COLUMNS);
        int width = columns + EXTRA_WIDTH_UNITS;
        if (!isScan(parameters)) {
            return new ComponentShape(new GridSize(width, rows + EXTRA_HEIGHT_UNITS), byRowsPins(rows, columns));
        }
        GridSize size = new GridSize(width, Math.max(rows + EXTRA_HEIGHT_UNITS, MINIMUM_SCAN_HEIGHT));
        return new ComponentShape(size, List.of(
                PinSpec.input("Fila", 0, 1, rowBits(rows)),
                PinSpec.input("Datos", 0, 1 + SCAN_PIN_SPACING, columns),
                PinSpec.input("Hab.", 0, 1 + 2 * SCAN_PIN_SPACING)));
    }

    private static List<PinSpec> byRowsPins(int rows, int columns) {
        List<PinSpec> pins = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            pins.add(PinSpec.input("F" + row, 0, row + 1, columns));
        }
        return pins;
    }
}
