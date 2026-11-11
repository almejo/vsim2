package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.geometry.Rotation;

import javax.swing.KeyStroke;
import java.util.Optional;

/** Reads the user's «Girar» shortcuts for the tools that turn a part before it is placed. */
public final class RotateShortcuts {

    private RotateShortcuts() {
    }

    /** True for the user's current «Girar» stroke, false for «Girar al revés», empty for any other stroke. */
    public static Optional<Boolean> clockwise(ToolContext context, KeyStroke stroke) {
        if (context.shortcut("edit.rotateClockwise").filter(stroke::equals).isPresent()) {
            return Optional.of(true);
        }
        if (context.shortcut("edit.rotateCounterClockwise").filter(stroke::equals).isPresent()) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    /** {@code rotation} turned as {@code stroke} asks, or empty when it is not a «Girar» stroke. */
    public static Optional<Rotation> turned(ToolContext context, KeyStroke stroke, Rotation rotation) {
        return clockwise(context, stroke).map(clockwise -> clockwise ? rotation.clockwise() : rotation.counterClockwise());
    }
}
