package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The «Aritmética» parts that follow the adder: subtractor, negator, multiplier, divider, shifter and bit counter.
 * They are {@link BlockSignals} blocks: «Ancho (bits)», «Retardo», data on the left, results on the right, control on
 * the top or bottom edge, a size that never depends on the width. Registered from here (not from
 * {@link StandardComponentTypes}) so each category of parts lives in its own class.
 */
public final class ArithmeticTypes {

    public static final String SUBTRACTOR = "subtractor";
    public static final String NEGATOR = "negator";
    public static final String MULTIPLIER = "multiplier";
    public static final String DIVIDER = "divider";
    public static final String SHIFTER = "shifter";
    public static final String BIT_COUNTER = "bit-counter";

    /** The shifter's «Tipo» parameter and its choices. */
    public static final String SHIFT_TYPE = "shiftType";
    public static final String LOGICAL_LEFT = "logical-left";
    public static final String LOGICAL_RIGHT = "logical-right";
    public static final String ARITHMETIC_RIGHT = "arithmetic-right";
    public static final String ROTATE_LEFT = "rotate-left";
    public static final String ROTATE_RIGHT = "rotate-right";
    static final List<String> SHIFT_TYPES = List.of(LOGICAL_LEFT, LOGICAL_RIGHT, ARITHMETIC_RIGHT, ROTATE_LEFT,
            ROTATE_RIGHT);

    private static final ParameterSpec SIGNEDNESS = ParameterSpec.choice(StandardComponentTypes.SIGNEDNESS,
            StandardComponentTypes.SIGNEDNESS_UNSIGNED,
            List.of(StandardComponentTypes.SIGNEDNESS_UNSIGNED, StandardComponentTypes.SIGNEDNESS_SIGNED));
    private static final ParameterSpec SHIFT_TYPE_PARAMETER = ParameterSpec.choice(SHIFT_TYPE, LOGICAL_LEFT,
            SHIFT_TYPES);

    private ArithmeticTypes() {
    }

    public static void register(ComponentTypeRegistry registry) {
        List<ParameterSpec> plain = List.of(BlockSignals.WIDTH, StandardComponentTypes.DELAY);
        List<ParameterSpec> withNumbers = List.of(BlockSignals.WIDTH, SIGNEDNESS, StandardComponentTypes.DELAY);
        registry.register(block(SUBTRACTOR, plain, ArithmeticTypes::subtractorShape,
                parameters -> new SubtractorBehavior(width(parameters), delay(parameters))));
        registry.register(block(NEGATOR, plain, ArithmeticTypes::negatorShape,
                parameters -> new NegatorBehavior(width(parameters), delay(parameters))));
        registry.register(block(MULTIPLIER, withNumbers, ArithmeticTypes::multiplierShape,
                parameters -> new MultiplierBehavior(width(parameters), isSigned(parameters), delay(parameters))));
        registry.register(block(DIVIDER, withNumbers, ArithmeticTypes::dividerShape,
                parameters -> new DividerBehavior(width(parameters), isSigned(parameters), delay(parameters))));
        registry.register(block(SHIFTER, List.of(BlockSignals.WIDTH, SHIFT_TYPE_PARAMETER,
                        StandardComponentTypes.DELAY), ArithmeticTypes::shifterShape,
                parameters -> new ShifterBehavior(width(parameters), (String) parameters.get(SHIFT_TYPE),
                        delay(parameters))));
        registry.register(block(BIT_COUNTER, plain, ArithmeticTypes::bitCounterShape,
                parameters -> new BitCounterBehavior(width(parameters), delay(parameters))));
    }

    private static ComponentType block(String id, List<ParameterSpec> parameters,
                                       Function<Integer, ComponentShape> shape,
                                       Function<Map<String, Object>, Behavior> behavior) {
        return ComponentType.parameterized(id, ComponentCategory.ARITHMETIC, parameters,
                resolved -> shape.apply(width(resolved)), behavior::apply);
    }

    private static int width(Map<String, Object> parameters) {
        return (Integer) parameters.get(StandardComponentTypes.WIDTH);
    }

    private static int delay(Map<String, Object> parameters) {
        return (Integer) parameters.get("delay");
    }

    private static boolean isSigned(Map<String, Object> parameters) {
        return StandardComponentTypes.SIGNEDNESS_SIGNED.equals(parameters.get(StandardComponentTypes.SIGNEDNESS));
    }

    /** Bits needed to write 0..{@code largest}; at least 1, because a pin is at least 1 bit wide. */
    static int bitsToCount(int largest) {
        return Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(largest));
    }

    /** The adder's 4 x 8 block: A, B at (0, 3), (0, 5); Bin on top; D at (4, 4); Bout at the bottom. */
    private static ComponentShape subtractorShape(int width) {
        return new ComponentShape(new GridSize(4, 8), List.of(
                PinSpec.input("A", 0, 3, width),
                PinSpec.input("B", 0, 5, width),
                PinSpec.input("Bin", 2, 0),
                PinSpec.output("D", 4, 4, width),
                PinSpec.output("Bout", 2, 8)));
    }

    /** A 4 x 6 block: A[N] at (0, 3), Y[N] at (4, 3). */
    private static ComponentShape negatorShape(int width) {
        return new ComponentShape(new GridSize(4, 6), List.of(
                PinSpec.input("A", 0, 3, width),
                PinSpec.output("Y", 4, 3, width)));
    }

    /** A 4 x 8 block: A, B at (0, 3), (0, 5); P (low half), H (high half) at (4, 3), (4, 5). */
    private static ComponentShape multiplierShape(int width) {
        return new ComponentShape(new GridSize(4, 8), List.of(
                PinSpec.input("A", 0, 3, width),
                PinSpec.input("B", 0, 5, width),
                PinSpec.output("P", 4, 3, width),
                PinSpec.output("H", 4, 5, width)));
    }

    /** A 6 x 8 block: A, B at (0, 3), (0, 5); Q, R at (6, 3), (6, 5); E (division by zero) at the bottom. */
    private static ComponentShape dividerShape(int width) {
        return new ComponentShape(new GridSize(6, 8), List.of(
                PinSpec.input("A", 0, 3, width),
                PinSpec.input("B", 0, 5, width),
                PinSpec.output("Q", 6, 3, width),
                PinSpec.output("R", 6, 5, width),
                PinSpec.output("E", 3, 8)));
    }

    /** A 6 x 8 block: A[N] at (0, 4), S[k] on the bottom edge, Y[N] at (6, 4). */
    private static ComponentShape shifterShape(int width) {
        return new ComponentShape(new GridSize(6, 8), List.of(
                PinSpec.input("A", 0, 4, width),
                PinSpec.input("S", 3, 8, bitsToCount(width - 1)),
                PinSpec.output("Y", 6, 4, width)));
    }

    /** A 4 x 6 block: A[N] at (0, 3), Y[k] at (4, 3). */
    private static ComponentShape bitCounterShape(int width) {
        return new ComponentShape(new GridSize(4, 6), List.of(
                PinSpec.input("A", 0, 3, width),
                PinSpec.output("Y", 4, 3, bitsToCount(width))));
    }
}
