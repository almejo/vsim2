package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;

/**
 * A combinational block (an adder, a multiplexer, a comparator…): its outputs follow only its inputs, so it works
 * them out when it joins the simulation and again after any input changes.
 */
abstract class CombinationalBehavior implements Behavior {

    @Override
    public final void initialize(BehaviorContext context) {
        evaluate(context);
    }

    @Override
    public final void inputChanged(BehaviorContext context, int pinIndex) {
        evaluate(context);
    }

    /** Drives every output from the inputs as they are now. */
    protected abstract void evaluate(BehaviorContext context);
}
