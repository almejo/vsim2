package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.List;

/**
 * The «Secuenciales» flip-flops beyond the D flip-flop: JK, T and SR. They share the D flip-flop's 4 x 4 box, clock in
 * the middle of the bottom edge (2, 4) and Q̅ ({@value #NOT_OUTPUT_PIN}) on the right edge under Q. T keeps D's rows
 * (T at (0, 2), Q at (4, 2), Q̅ at (4, 3)) so it can take a D flip-flop's place; JK and SR have their two inputs and
 * their two outputs at y = 1 and y = 3. Pins: inputs first, then the clock, Q and Q̅. See
 * {@link ClockedFlipFlopBehavior} for what they share (falling edge, start at Q = 0).
 */
public final class SequentialTypes {

    /** «Flip-flop JK». */
    public static final String FLIP_FLOP_JK = "flip-flop-jk";
    /** «Flip-flop T». */
    public static final String FLIP_FLOP_T = "flip-flop-t";
    /** «Flip-flop SR». */
    public static final String FLIP_FLOP_SR = "flip-flop-sr";
    /** Every flip-flop type, the D flip-flop first. */
    public static final List<String> FLIP_FLOPS = List.of(StandardComponentTypes.FLIP_FLOP_DATA, FLIP_FLOP_JK,
            FLIP_FLOP_T, FLIP_FLOP_SR);
    /** The name of every flip-flop's Q̅ pin. */
    public static final String NOT_OUTPUT_PIN = "nQ";

    private static final GridSize FLIP_FLOP_SIZE = new GridSize(4, 4);
    private static final PinSpec CLOCK = PinSpec.input("clock", 2, 4);

    private SequentialTypes() {
    }

    /** Registers the JK, T and SR flip-flops, in that order (the palette lists them after the D flip-flop). */
    static void register(ComponentTypeRegistry registry) {
        registry.register(new ComponentType(FLIP_FLOP_JK, ComponentCategory.SEQUENTIAL, FLIP_FLOP_SIZE,
                twoInputPins("j", "k"), List.of(StandardComponentTypes.DELAY),
                parameters -> new FlipFlopJkBehavior(delay(parameters.get("delay")))));
        registry.register(new ComponentType(FLIP_FLOP_T, ComponentCategory.SEQUENTIAL, FLIP_FLOP_SIZE,
                List.of(PinSpec.input("t", 0, 2), CLOCK, PinSpec.output("q", 4, 2),
                        PinSpec.output(NOT_OUTPUT_PIN, 4, 3)),
                List.of(StandardComponentTypes.DELAY),
                parameters -> new FlipFlopTBehavior(delay(parameters.get("delay")))));
        registry.register(new ComponentType(FLIP_FLOP_SR, ComponentCategory.SEQUENTIAL, FLIP_FLOP_SIZE,
                twoInputPins("s", "r"), List.of(StandardComponentTypes.DELAY),
                parameters -> new FlipFlopSrBehavior(delay(parameters.get("delay")))));
    }

    private static List<PinSpec> twoInputPins(String first, String second) {
        return List.of(PinSpec.input(first, 0, 1), PinSpec.input(second, 0, 3), CLOCK, PinSpec.output("q", 4, 1),
                PinSpec.output(NOT_OUTPUT_PIN, 4, 3));
    }

    private static int delay(Object value) {
        return (Integer) value;
    }
}
