package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

import java.util.OptionalLong;

/**
 * «Extensor de bits»: pins in[N], out[M]. Wider, the new upper bits are zeros or copies of the top bit of the input
 * (the sign); narrower, only the M low bits are kept. No delay (it is wiring); an undefined input drives all Z.
 */
final class BitExtenderBehavior implements Behavior {

    private final int outputWidth;
    private final boolean signExtended;

    BitExtenderBehavior(int outputWidth, boolean signExtended) {
        this.outputWidth = outputWidth;
        this.signExtended = signExtended;
    }

    @Override
    public void initialize(BehaviorContext context) {
        evaluate(context);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        evaluate(context);
    }

    private void evaluate(BehaviorContext context) {
        Value input = context.input(0);
        OptionalLong number = signExtended ? BlockSignals.signed(input) : BlockSignals.unsigned(input);
        context.drive(1, number.isEmpty() ? BlockSignals.undefined(outputWidth)
                : BlockSignals.result(outputWidth, number.getAsLong()), 0);
    }
}
