package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;

/**
 * A reminder the user sticks on the circuit: a paper at {@code position} (top-left) of {@code width} x
 * {@code height} grid units, with free text or a checklist. It takes no part in the netlist or the simulation.
 * Folded, it shows as a small paper icon of {@link #FOLDED_SIZE} units.
 */
public record StickyNote(ElementId id, GridPoint position, int width, int height, StickyColor color,
                         StickyContent content, boolean folded) implements DocumentElement {

    public static final int DEFAULT_WIDTH = 8;
    public static final int DEFAULT_HEIGHT = 6;
    public static final int MINIMUM_WIDTH = 4;
    public static final int MINIMUM_HEIGHT = 3;
    public static final int FOLDED_SIZE = 2;

    public StickyNote {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(content, "content");
        width = Math.max(width, MINIMUM_WIDTH);
        height = Math.max(height, MINIMUM_HEIGHT);
    }

    public StickyNote withContent(StickyContent newContent) {
        return new StickyNote(id, position, width, height, color, newContent, folded);
    }

    public StickyNote withColor(StickyColor newColor) {
        return new StickyNote(id, position, width, height, newColor, content, folded);
    }

    public StickyNote withSize(int newWidth, int newHeight) {
        return new StickyNote(id, position, newWidth, newHeight, color, content, folded);
    }

    public StickyNote withFolded(boolean newFolded) {
        return new StickyNote(id, position, width, height, color, content, newFolded);
    }

    /** This note under another id at another place (a paste). */
    public StickyNote placed(ElementId newId, GridPoint newPosition) {
        return new StickyNote(newId, newPosition, width, height, color, content, folded);
    }

    public int visibleWidth() {
        return folded ? FOLDED_SIZE : width;
    }

    public int visibleHeight() {
        return folded ? FOLDED_SIZE : height;
    }

    @Override
    public StickyNote translate(int deltaX, int deltaY) {
        return placed(id, position.translate(deltaX, deltaY));
    }
}
