package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.BehaviorContext;

import java.util.Optional;

/**
 * D flip-flop: pin 0 D, pin 1 Q, pin 2 clock, pin 3 Q̅ (appended in format 5, so older files keep their pin indices).
 * Latches D on a falling edge; Q copies D as it is (Z included), Q̅ is its complement or Z.
 */
final class FlipFlopDataBehavior extends ClockedFlipFlopBehavior {

    static final int DATA = 0;
    static final int OUTPUT = 1;
    static final int CLOCK = 2;
    static final int NOT_OUTPUT = 3;

    FlipFlopDataBehavior(int delay) {
        super(CLOCK, OUTPUT, NOT_OUTPUT, delay);
    }

    @Override
    Optional<Outputs> next(BehaviorContext context, Outputs before) {
        return Optional.of(Outputs.of(context.input(DATA)));
    }
}
