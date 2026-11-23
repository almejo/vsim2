package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.document.StickyColor;

import java.util.Objects;

/**
 * A sticky note on a machine's diagram: a paper at {@code position} (top-left) of {@code width} x {@code height}
 * diagram units, with free text and one of the circuit notes' colours, {@code folded} into a small paper icon as a
 * circuit's sticky note folds (its size is kept for when it unfolds). It is only a reminder for the reader: it takes
 * no part in the simulation and never shows on the piece's face.
 */
public record MachineNote(String id, DiagramPoint position, double width, double height, StickyColor color,
                          String text, boolean folded) {

    /**
     * Two state circles wide ({@code STATE_RADIUS} is 40): the circuit note's 8 x 6 grid at 8 px (64 x 48) looked
     * smaller than a state, too small to hold a sentence next to it.
     */
    public static final double DEFAULT_WIDTH = 160;
    public static final double DEFAULT_HEIGHT = 120;
    /** The circuit note's default size at zoom 1: the smallest that still holds a word or two. */
    public static final double MINIMUM_WIDTH = 64;
    public static final double MINIMUM_HEIGHT = 48;

    public MachineNote {
        StateMachine.requireId(id, "note");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(text, "text");
        if (!Double.isFinite(width) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("note " + id + " needs a finite size: " + width + "x" + height);
        }
        width = Math.max(width, MINIMUM_WIDTH);
        height = Math.max(height, MINIMUM_HEIGHT);
    }

    /** An unfolded note. */
    public MachineNote(String id, DiagramPoint position, double width, double height, StickyColor color,
                       String text) {
        this(id, position, width, height, color, text, false);
    }

    public MachineNote withPosition(DiagramPoint newPosition) {
        return new MachineNote(id, newPosition, width, height, color, text, folded);
    }

    public MachineNote withSize(double newWidth, double newHeight) {
        return new MachineNote(id, position, newWidth, newHeight, color, text, folded);
    }

    public MachineNote withColor(StickyColor newColor) {
        return new MachineNote(id, position, width, height, newColor, text, folded);
    }

    public MachineNote withText(String newText) {
        return new MachineNote(id, position, width, height, color, newText, folded);
    }

    /** This note folded into its small paper icon, or unfolded to its size. */
    public MachineNote withFolded(boolean newFolded) {
        return new MachineNote(id, position, width, height, color, text, newFolded);
    }

    /** This note under another id at another place (a paste). */
    public MachineNote placed(String newId, DiagramPoint newPosition) {
        return new MachineNote(newId, newPosition, width, height, color, text, folded);
    }
}
