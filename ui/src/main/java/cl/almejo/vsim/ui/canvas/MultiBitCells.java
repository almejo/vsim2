package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Which bit of a multi-bit Switch or LED is under a point of the canvas: the click toggles it and the tooltip names
 * it. The body is drawn upright, so a point is mapped back into the body's own pixels (undoing the element's rotation
 * and the lead on the side of the pin) before looking for a {@link MultiBitLayout.BitCell}.
 */
public final class MultiBitCells {

    private MultiBitCells() {
    }

    /** The bit under {@code gridPosition} on {@code component} (of {@code type}), if the point is on a cell. */
    public static OptionalInt bitAt(ComponentType type, Component component, Point2D gridPosition) {
        if (!MultiBitLayout.isMultiBit(type)) {
            return OptionalInt.empty();
        }
        int width = MultiBitLayout.widthOf(type);
        String side = MultiBitLayout.sideOf(type, component.parameters());
        Point2D local = unrotated(component.rotation(), gridPosition.getX() - component.position().x(),
                gridPosition.getY() - component.position().y());
        GridPoint origin = MultiBitLayout.bodyOrigin(width, side);
        Optional<MultiBitLayout.BitCell> cell = MultiBitLayout.cellAt(width,
                (local.getX() - origin.x()) * GridPoint.PIXELS_PER_UNIT,
                (local.getY() - origin.y()) * GridPoint.PIXELS_PER_UNIT);
        return cell.map(found -> OptionalInt.of(found.bit())).orElse(OptionalInt.empty());
    }

    /** The inverse of {@link Rotation#apply}: a point of the rotated component back in its own frame. */
    private static Point2D unrotated(Rotation rotation, double pointX, double pointY) {
        return switch (rotation) {
            case R0 -> new Point2D.Double(pointX, pointY);
            case R90 -> new Point2D.Double(pointY, -pointX);
            case R180 -> new Point2D.Double(-pointX, -pointY);
            case R270 -> new Point2D.Double(-pointY, pointX);
        };
    }
}
