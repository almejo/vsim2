package cl.almejo.vsim.core.machine;

import java.util.Objects;
import java.util.Set;

/**
 * The limits of a «Máquina de estados» (spec §1: 16 inputs, 16 outputs, 64 states, so the part, its diagram and
 * the conversion to a circuit stay manageable) and the pin names every machine has and the user cannot take.
 */
public final class MachineLimits {

    public static final int MAXIMUM_INPUTS = 16;
    public static final int MAXIMUM_OUTPUTS = 16;
    public static final int MAXIMUM_STATES = 64;

    /** The clock pin; the machine changes state on its edge. */
    public static final String CLOCK = "CLK";
    /** The asynchronous, active-high reset pin (spec §4.1). */
    public static final String RESET = "RST";

    private static final Set<String> RESERVED_NAMES = Set.of(CLOCK, RESET);

    private MachineLimits() {
    }

    /** Whether {@code name} is a pin every machine has ({@code CLK}, {@code RST}; case matters, like pin names). */
    public static boolean isReserved(String name) {
        return RESERVED_NAMES.contains(Objects.requireNonNull(name, "name"));
    }

    /** Whether one more state still fits in {@code machine}. */
    public static boolean canAddState(StateMachine machine) {
        return machine.states().size() < MAXIMUM_STATES;
    }
}
