package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * «Punto de parada»: asks the simulation to pause ({@link BehaviorContext#requestPause}) each time its input changes to a
 * value that meets the condition: «igual a 9» stops each time the input arrives at 9, «distinto de 0» at every new value
 * other than 0 (a counter stops at 1, 2, 3…; the user's hand test, 2026-11-07). The same value read again is no change. An input with a Z bit or a conflict never satisfies it, not even
 * «distinto de»: the student reads «not driven» as «nothing to compare». A rebuilt behaviour (a parameter edit) starts
 * from what its input reads at that moment, so changing «Valor» while the input holds it does not stop at once.
 */
final class BreakpointBehavior implements Behavior {

    private static final int INPUT = 0;

    private final int width;
    private final boolean equalsCondition;
    private final long target;
    private final boolean active;
    private OptionalLong lastRead = OptionalLong.empty();

    BreakpointBehavior(int width, boolean equalsCondition, long target, boolean active) {
        this.width = width;
        this.equalsCondition = equalsCondition;
        this.target = target;
        this.active = active;
    }

    static BreakpointBehavior from(Map<String, Object> parameters) {
        int width = (Integer) parameters.get(StandardComponentTypes.WIDTH);
        long target = ConstantValue.bitsOf((String) parameters.get(StandardComponentTypes.VALUE), width).orElseThrow();
        return new BreakpointBehavior(width, BreakpointTypes.isEquals(parameters), target,
                (Boolean) parameters.get(BreakpointTypes.ACTIVE));
    }

    @Override
    public void initialize(BehaviorContext context) {
        lastRead = BlockSignals.unsigned(context.input(INPUT));
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        Value input = context.input(INPUT);
        OptionalLong read = BlockSignals.unsigned(input);
        boolean changed = !read.equals(lastRead);
        lastRead = read;
        if (changed && holds(input) && active) {
            context.requestPause(equalsCondition ? BreakpointTypes.STOPPED_EQUALS_MESSAGE
                    : BreakpointTypes.STOPPED_DIFFERS_MESSAGE, arguments(input));
        }
    }

    private boolean holds(Value input) {
        OptionalLong bits = BlockSignals.unsigned(input);
        return bits.isPresent() && (bits.getAsLong() == target) == equalsCondition;
    }

    private List<String> arguments(Value input) {
        String read = shown(input.bits());
        return equalsCondition ? List.of(read) : List.of(read, shown(target));
    }

    private String shown(long bits) {
        return ConstantValue.display(bits, width, ConstantValue.AUTO);
    }
}
