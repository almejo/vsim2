package cl.almejo.vsim.ui.resize;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ResizeSpec;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Where the «↔» tab of a resizable part is (Teclado mockup v4, {@code gripBounds}): a 14 × 8 px tab on the selection
 * outline over the corner where the moving edge meets the top, away from the pins. For the right edge it is
 * {@code (width − 9, −10, 14, 8)} in the part's own pixels (unrotated, 8 px per grid unit); for the bottom edge the
 * same tab turned a quarter, over the left end of that edge. A press within 3 px of it takes it.
 */
final class ResizeGrip {

    static final double TAB_LENGTH = 14;
    static final double TAB_THICKNESS = 8;
    /** How far the tab starts before the moving edge. */
    private static final double TAB_INSET = 9;
    /** How far above the part (or left of it, for the bottom edge) the tab starts. */
    private static final double TAB_RISE = 10;
    /** The room around the tab that still takes a press (mockup: 3 px). */
    private static final double HIT_MARGIN = 3;
    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;

    private ResizeGrip() {
    }

    /** The tab in the part's own pixels, for a part of {@code size} that grows at {@code edge}. */
    static Rectangle2D localBounds(ResizeSpec.Edge edge, GridSize size) {
        return switch (edge) {
            case RIGHT -> new Rectangle2D.Double(size.width() * UNIT - TAB_INSET, -TAB_RISE, TAB_LENGTH,
                    TAB_THICKNESS);
            case BOTTOM -> new Rectangle2D.Double(-TAB_RISE, size.height() * UNIT - TAB_INSET, TAB_THICKNESS,
                    TAB_LENGTH);
        };
    }

    /** The tab of {@code component}, whose type (with its parameters) is {@code type}, in its own pixels. */
    static Rectangle2D localBounds(Component component, ComponentType type) {
        return localBounds(type.resize().orElseThrow().edge(), type.forComponent(component).size());
    }

    /** True when {@code gridPosition} (document grid units) is on the tab or within 3 px of it. */
    static boolean hits(Component component, ComponentType type, Point2D gridPosition) {
        Rectangle2D tab = localBounds(component, type);
        Point2D local = toLocalPixels(component, gridPosition);
        return local.getX() >= tab.getMinX() - HIT_MARGIN && local.getX() <= tab.getMaxX() + HIT_MARGIN
                && local.getY() >= tab.getMinY() - HIT_MARGIN && local.getY() <= tab.getMaxY() + HIT_MARGIN;
    }

    /** The tab's centre in document grid units. */
    static Point2D centre(Component component, ComponentType type) {
        Rectangle2D tab = localBounds(component, type);
        return toDocumentGrid(component, new Point2D.Double(tab.getCenterX(), tab.getCenterY()));
    }

    /** {@code gridPosition} (document grid units) in {@code component}'s own unrotated pixels. */
    static Point2D toLocalPixels(Component component, Point2D gridPosition) {
        Point2D offset = new Point2D.Double(gridPosition.getX() - component.position().x(),
                gridPosition.getY() - component.position().y());
        Point2D local = turned(Rotation.fromQuarterTurns(-component.rotation().quarterTurns()), offset);
        return new Point2D.Double(local.getX() * UNIT, local.getY() * UNIT);
    }

    /** A vector of the document (grid units) along {@code component}'s own unrotated axes. */
    static Point2D toLocalVector(Component component, double deltaX, double deltaY) {
        return turned(Rotation.fromQuarterTurns(-component.rotation().quarterTurns()),
                new Point2D.Double(deltaX, deltaY));
    }

    /** A point in {@code component}'s own pixels, in document grid units. */
    static Point2D toDocumentGrid(Component component, Point2D localPixels) {
        Point2D turned = turned(component.rotation(),
                new Point2D.Double(localPixels.getX() / UNIT, localPixels.getY() / UNIT));
        return new Point2D.Double(component.position().x() + turned.getX(),
                component.position().y() + turned.getY());
    }

    /** {@link Rotation#apply} for a point that need not be on the grid. */
    private static Point2D turned(Rotation rotation, Point2D point) {
        return switch (rotation) {
            case R0 -> point;
            case R90 -> new Point2D.Double(-point.getY(), point.getX());
            case R180 -> new Point2D.Double(-point.getX(), -point.getY());
            case R270 -> new Point2D.Double(point.getY(), -point.getX());
        };
    }
}
