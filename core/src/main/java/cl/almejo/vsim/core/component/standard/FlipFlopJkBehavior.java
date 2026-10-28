package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;

/**
 * JK flip-flop: pins J, K, clock, Q, Q̅. On a falling edge: J K = 00 holds, 01 resets, 10 sets, 11 toggles. J or K
 * undefined (Z, unconnected or a conflict) at the edge leaves both outputs in Z.
 */
final class FlipFlopJkBehavior extends ClockedFlipFlopBehavior {

    static final int JAY = 0;
    static final int KAY = 1;
    static final int CLOCK = 2;
    static final int OUTPUT = 3;
    static final int NOT_OUTPUT = 4;

    FlipFlopJkBehavior(int delay) {
        super(CLOCK, OUTPUT, NOT_OUTPUT, delay);
    }

    @Override
    Optional<Outputs> next(BehaviorContext context, Outputs before) {
        Optional<Boolean> jay = bit(context.input(JAY));
        Optional<Boolean> kay = bit(context.input(KAY));
        if (jay.isEmpty() || kay.isEmpty()) {
            return Optional.of(Outputs.UNDEFINED);
        }
        if (jay.get() && kay.get()) {
            return Optional.of(toggled(before));
        }
        if (jay.get()) {
            return Optional.of(Outputs.of(Value.ONE));
        }
        if (kay.get()) {
            return Optional.of(Outputs.of(Value.ZERO));
        }
        return Optional.empty();
    }
}
