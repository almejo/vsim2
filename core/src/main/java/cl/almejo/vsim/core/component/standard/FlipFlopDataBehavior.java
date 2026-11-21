package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.Optional;

/**
 * D flip-flop: pin 0 D, pin 1 Q, pin 2 clock, pin 3 Q̅ when «Mostrar Q̅» is on (appended in format 5, so older files
 * keep their pin indices). Latches D on a falling edge; Q copies D when it is a defined bit, and Q and Q̅ are both Z
 * when D is Z or a bus conflict (a short is never stored as a clean bit, as in Register and Counter).
 */
final class FlipFlopDataBehavior extends ClockedFlipFlopBehavior {

    static final int DATA = 0;
    static final int OUTPUT = 1;
    static final int CLOCK = 2;
    static final int NOT_OUTPUT = 3;

    FlipFlopDataBehavior(int delay, boolean showInverted) {
        super(CLOCK, OUTPUT, showInverted ? NOT_OUTPUT : NO_PIN, delay);
    }

    @Override
    Optional<Outputs> next(BehaviorContext context, Outputs before) {
        return Optional.of(Outputs.of(stored(context.input(DATA))));
    }

    private static Value stored(Value data) {
        return data.isFullyDefined() ? data : Value.HIGH_IMPEDANCE;
    }
}
