package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.BehaviorContext;

import java.util.Optional;

/**
 * T flip-flop: pins T, clock, Q, Q̅. On a falling edge T = 1 toggles Q and T = 0 holds it. T undefined (Z,
 * unconnected or a conflict) at the edge leaves both outputs in Z.
 */
final class FlipFlopTBehavior extends ClockedFlipFlopBehavior {

    static final int TOGGLE = 0;
    static final int CLOCK = 1;
    static final int OUTPUT = 2;
    static final int NOT_OUTPUT = 3;

    FlipFlopTBehavior(int delay) {
        super(CLOCK, OUTPUT, NOT_OUTPUT, delay);
    }

    @Override
    Optional<Outputs> next(BehaviorContext context, Outputs before) {
        Optional<Boolean> toggle = bit(context.input(TOGGLE));
        if (toggle.isEmpty()) {
            return Optional.of(Outputs.UNDEFINED);
        }
        return toggle.get() ? Optional.of(toggled(before)) : Optional.empty();
    }
}
