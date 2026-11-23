package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;

/** The edge of a component's box a pin lies on (the block and memory painters draw its lead from there), if any. */
enum PinSide {
    LEFT, RIGHT, TOP, BOTTOM, NONE;

    /** The edge {@code pin} lies on, the left and right ones first at a corner; {@link #NONE} inside the box. */
    static PinSide of(ComponentType type, PinSpec pin) {
        int x = pin.position().x();
        int y = pin.position().y();
        if (x <= 0) {
            return LEFT;
        }
        if (x >= type.size().width()) {
            return RIGHT;
        }
        if (y <= 0) {
            return TOP;
        }
        if (y >= type.size().height()) {
            return BOTTOM;
        }
        return NONE;
    }
}
