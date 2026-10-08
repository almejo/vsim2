package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.value.Value;

/** AND/OR/XOR/NAND/NOR of N inputs (port of VSim's AssociativePin); the output pin index is N. */
record LogicGateBehavior(LogicOperation operation, int inputCount, int delay) implements Behavior {

    @Override
    public void initialize(BehaviorContext context) {
        evaluate(context);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        evaluate(context);
    }

    private void evaluate(BehaviorContext context) {
        Value value = context.input(0);
        for (int index = 1; index < inputCount; index++) {
            value = operation.combine(value, context.input(index));
        }
        if (operation.negated()) {
            value = value.not();
        }
        context.drive(inputCount, value, delay);
    }
}
