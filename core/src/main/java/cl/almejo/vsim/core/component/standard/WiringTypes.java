package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.List;

/**
 * The parts of «Cableado» added for classes: the {@link #BIT_EXTENDER} and the {@link #BIT_SELECTOR}. Like the other
 * blocks their size never depends on the widths; the splitter, the joiner and the tunnels stay in
 * {@link StandardComponentTypes}.
 */
public final class WiringTypes {

    /** «Extensor de bits»: widens or cuts a bus; no delay, it is only wiring. */
    public static final String BIT_EXTENDER = "bit-extender";
    /** «Selector de bits»: the numbered group of bits of a bus. */
    public static final String BIT_SELECTOR = "bit-selector";

    /** «Ancho de entrada» of both parts: 1..32 bits, 8 by default. */
    public static final String INPUT_WIDTH = "inputWidth";
    /** «Ancho de salida» of both parts: 1..32 bits; 16 for the extender, 1 for the selector. */
    public static final String OUTPUT_WIDTH = "outputWidth";
    /** «Relleno» of the extender: the new upper bits are {@link #FILL_ZEROS} or copies of the sign bit. */
    public static final String FILL = "fill";
    public static final String FILL_ZEROS = "zeros";
    public static final String FILL_SIGN = "sign";

    private static final int MAXIMUM_WIDTH = 32;
    private static final ParameterSpec INPUT_WIDTH_SPEC = ParameterSpec.integer(INPUT_WIDTH, 8, 1, MAXIMUM_WIDTH);
    private static final GridSize EXTENDER_SIZE = new GridSize(6, 4);
    private static final GridSize SELECTOR_SIZE = new GridSize(6, 6);

    private WiringTypes() {
    }

    /** Registers the bit extender and the bit selector. */
    public static void register(ComponentTypeRegistry registry) {
        registry.register(ComponentType.parameterized(BIT_EXTENDER, ComponentCategory.WIRING,
                List.of(INPUT_WIDTH_SPEC, ParameterSpec.integer(OUTPUT_WIDTH, 16, 1, MAXIMUM_WIDTH),
                        ParameterSpec.choice(FILL, FILL_ZEROS, List.of(FILL_ZEROS, FILL_SIGN))),
                parameters -> extenderShape((Integer) parameters.get(INPUT_WIDTH),
                        (Integer) parameters.get(OUTPUT_WIDTH)),
                parameters -> new BitExtenderBehavior((Integer) parameters.get(OUTPUT_WIDTH),
                        FILL_SIGN.equals(parameters.get(FILL)))));
        registry.register(ComponentType.parameterized(BIT_SELECTOR, ComponentCategory.WIRING,
                List.of(INPUT_WIDTH_SPEC, ParameterSpec.integer(OUTPUT_WIDTH, 1, 1, MAXIMUM_WIDTH),
                        StandardComponentTypes.DELAY),
                parameters -> selectorShape((Integer) parameters.get(INPUT_WIDTH),
                        (Integer) parameters.get(OUTPUT_WIDTH)),
                parameters -> new BitSelectorBehavior((Integer) parameters.get(OUTPUT_WIDTH),
                        (Integer) parameters.get("delay"))));
    }

    /** The select input's width: the bits that number the groups, at least 1 (a single group still has the pin). */
    static int selectWidth(int inputWidth, int outputWidth) {
        int groups = (inputWidth + outputWidth - 1) / outputWidth;
        return Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(groups - 1));
    }

    /** A 6 x 4 box: in[N] at (0, 2), out[M] at (6, 2). */
    private static ComponentShape extenderShape(int inputWidth, int outputWidth) {
        return new ComponentShape(EXTENDER_SIZE, List.of(
                PinSpec.input("in", 0, 2, inputWidth),
                PinSpec.output("out", EXTENDER_SIZE.width(), 2, outputWidth)));
    }

    /** A 6 x 6 box: in[N] at (0, 3), out[M] at (6, 3) and the group number S on the bottom edge. */
    private static ComponentShape selectorShape(int inputWidth, int outputWidth) {
        return new ComponentShape(SELECTOR_SIZE, List.of(
                PinSpec.input("in", 0, 3, inputWidth),
                PinSpec.input("S", 3, SELECTOR_SIZE.height(), selectWidth(inputWidth, outputWidth)),
                PinSpec.output("out", SELECTOR_SIZE.width(), 3, outputWidth)));
    }
}
