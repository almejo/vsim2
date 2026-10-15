package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;

/**
 * A titled rectangle that annotates a circuit. It takes no part in the netlist or the simulation. {@code locked}
 * decides whether moving it carries its contents along (see {@link FrameContents}).
 */
public record Frame(ElementId id, GridPoint topLeft, int width, int height, String title, FrameTint tint,
                    boolean locked) implements DocumentElement {

    public static final int MINIMUM_WIDTH = 6;
    public static final int MINIMUM_HEIGHT = 4;

    public Frame {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(topLeft, "topLeft");
        if (width < MINIMUM_WIDTH || height < MINIMUM_HEIGHT) {
            throw new IllegalArgumentException("frame too small: " + width + "x" + height + ", minimum "
                    + MINIMUM_WIDTH + "x" + MINIMUM_HEIGHT);
        }
        title = title == null ? "" : title;
        tint = tint == null ? FrameTint.NONE : tint;
    }

    public Frame withTitle(String newTitle) {
        return new Frame(id, topLeft, width, height, newTitle, tint, locked);
    }

    public Frame withTint(FrameTint newTint) {
        return new Frame(id, topLeft, width, height, title, newTint, locked);
    }

    public Frame withLocked(boolean newLocked) {
        return new Frame(id, topLeft, width, height, title, tint, newLocked);
    }

    public Frame withRectangle(GridPoint newTopLeft, int newWidth, int newHeight) {
        return new Frame(id, newTopLeft, newWidth, newHeight, title, tint, locked);
    }

    /** True for points on the border or inside. */
    public boolean contains(GridPoint point) {
        return point.x() >= topLeft.x() && point.x() <= topLeft.x() + width
                && point.y() >= topLeft.y() && point.y() <= topLeft.y() + height;
    }

    /** True when the rectangle {@code left..right} x {@code top..bottom} lies completely inside (border included). */
    public boolean encloses(int left, int top, int right, int bottom) {
        return left >= topLeft.x() && right <= topLeft.x() + width
                && top >= topLeft.y() && bottom <= topLeft.y() + height;
    }

    @Override
    public Frame translate(int deltaX, int deltaY) {
        return new Frame(id, topLeft.translate(deltaX, deltaY), width, height, title, tint, locked);
    }
}
