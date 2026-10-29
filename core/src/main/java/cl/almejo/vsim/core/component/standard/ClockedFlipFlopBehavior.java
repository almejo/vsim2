package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;

/**
 * What every flip-flop shares (D, JK, T, SR): it acts only on the clock's falling edge (ONE to ZERO; a clock that goes
 * through Z gives no edge), starts at Q = 0 and Q̅ = 1 (power-on reset, as in teaching simulators, so a toggle or a
 * ripple counter starts), and drives both outputs {@code delay} after the edge. Each kind only says, in
 * {@link #next}, what its outputs become at an edge.
 */
abstract sealed class ClockedFlipFlopBehavior implements Behavior
        permits FlipFlopDataBehavior, FlipFlopJkBehavior, FlipFlopTBehavior, FlipFlopSrBehavior {

    /** The two outputs; normally complementary, but the SR forbidden state has both at 0. */
    record Outputs(Value output, Value notOutput) {

        static final Outputs POWER_ON = new Outputs(Value.ZERO, Value.ONE);
        /** Q = 1 and Q̅ = 0. */
        static final Outputs SET = new Outputs(Value.ONE, Value.ZERO);
        /** Both outputs in Z: an input was undefined at the edge. */
        static final Outputs UNDEFINED = new Outputs(Value.HIGH_IMPEDANCE, Value.HIGH_IMPEDANCE);

        /** Q = {@code output} and Q̅ its complement (Z when Q is not a defined bit). */
        static Outputs of(Value output) {
            if (output.equals(Value.ZERO)) {
                return POWER_ON;
            }
            if (output.equals(Value.ONE)) {
                return SET;
            }
            return new Outputs(output, output.isFullyDefined() ? output.not() : Value.HIGH_IMPEDANCE);
        }
    }

    /** {@code notOutputPin} of a flip-flop without Q̅ (a D flip-flop with «Mostrar Q̅» off). */
    static final int NO_PIN = -1;

    private final int clockPin;
    private final int outputPin;
    private final int notOutputPin;
    private final int delay;
    private Value previousClock = Value.HIGH_IMPEDANCE;
    private Outputs current = Outputs.POWER_ON;

    ClockedFlipFlopBehavior(int clockPin, int outputPin, int notOutputPin, int delay) {
        this.clockPin = clockPin;
        this.outputPin = outputPin;
        this.notOutputPin = notOutputPin;
        this.delay = delay;
    }

    /** The outputs after a falling edge, given the ones before it; empty to keep them («hold»). */
    abstract Optional<Outputs> next(BehaviorContext context, Outputs before);

    /** {@code input} as a bit, empty when it is Z (unconnected) or a conflict. */
    static Optional<Boolean> bit(Value input) {
        return input.isFullyDefined() ? Optional.of(input.bits() != 0L) : Optional.empty();
    }

    /** The outputs after a toggle: Q inverted, or both in Z when Q was not a defined bit. */
    static Outputs toggled(Outputs before) {
        return before.output().isFullyDefined() ? Outputs.of(before.output().not()) : Outputs.UNDEFINED;
    }

    @Override
    public final void initialize(BehaviorContext context) {
        previousClock = context.input(clockPin);
        // Power-on reset: Q is 0 and Q̅ is 1 before the first falling edge (an unconnected input still reads Z).
        drive(context, current, 0);
    }

    @Override
    public final void inputChanged(BehaviorContext context, int pinIndex) {
        if (pinIndex != clockPin) {
            return;
        }
        Value clock = context.input(clockPin);
        boolean fallingEdge = previousClock.equals(Value.ONE) && clock.equals(Value.ZERO);
        previousClock = clock;
        if (!fallingEdge) {
            return;
        }
        next(context, current).ifPresent(outputs -> {
            current = outputs;
            drive(context, outputs, delay);
        });
    }

    private void drive(BehaviorContext context, Outputs outputs, int after) {
        context.drive(outputPin, outputs.output(), after);
        if (notOutputPin != NO_PIN) {
            context.drive(notOutputPin, outputs.notOutput(), after);
        }
    }
}
