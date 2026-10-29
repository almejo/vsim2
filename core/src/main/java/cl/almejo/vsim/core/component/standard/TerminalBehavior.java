package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;

/** A first Terminal: its pins exist and can be wired, but nothing is read from them and nothing is driven. */
final class TerminalBehavior implements Behavior {

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        // nothing yet: the screen comes later
    }
}
