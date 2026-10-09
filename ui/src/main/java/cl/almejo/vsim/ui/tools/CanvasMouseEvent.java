package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.geom.Point2D;

/** A mouse event already translated to grid coordinates; tools never see AWT events. */
public record CanvasMouseEvent(Point2D gridPosition, GridPoint snapped, Point2D screenPosition, int button,
                               boolean shiftDown, boolean controlDown, int clickCount) {
    public static final int PRIMARY = 1;
    public static final int MIDDLE = 2;
    public static final int SECONDARY = 3;
}
