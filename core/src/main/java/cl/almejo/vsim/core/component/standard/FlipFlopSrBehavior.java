package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;

/**
 * SR flip-flop: pins S, R, clock, Q, Q̅. On a falling edge: S R = 00 holds, 01 resets, 10 sets, and 11 is the
 * forbidden state, shown as Q = Q̅ = 0 (what a NOR latch shows) until an edge with a valid combination; 00 holds it
 * too. S or R undefined (Z, unconnected or a conflict) at the edge leaves both outputs in Z.
 */
final class FlipFlopSrBehavior extends ClockedFlipFlopBehavior {

    static final int SET = 0;
    static final int RESET = 1;
    static final int CLOCK = 2;
    static final int OUTPUT = 3;
    static final int NOT_OUTPUT = 4;
    private static final Outputs FORBIDDEN = new Outputs(Value.ZERO, Value.ZERO);

    FlipFlopSrBehavior(int delay) {
        super(CLOCK, OUTPUT, NOT_OUTPUT, delay);
    }

    @Override
    Optional<Outputs> next(BehaviorContext context, Outputs before) {
        Optional<Boolean> set = bit(context.input(SET));
        Optional<Boolean> reset = bit(context.input(RESET));
        if (set.isEmpty() || reset.isEmpty()) {
            return Optional.of(Outputs.UNDEFINED);
        }
        if (set.get() && reset.get()) {
            return Optional.of(FORBIDDEN);
        }
        if (set.get()) {
            return Optional.of(Outputs.of(Value.ONE));
        }
        if (reset.get()) {
            return Optional.of(Outputs.of(Value.ZERO));
        }
        return Optional.empty();
    }
}
