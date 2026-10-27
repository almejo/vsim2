package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Side;

import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Drags a divider (canvas-edges §1, spec §2): continuously, the panes follow; at the crossing point both dividers
 * move; released within {@link CanvasPanes#EDGE_MARGIN} of an edge the divider goes, closing the panes on that side; a
 * double-click removes it (both at the crossing point), closing the panes after it.
 */
final class DividerDrag extends MouseAdapter {

    private final CanvasPanes panes;
    private final PaneDivider divider;
    /** The dividers being dragged, and how far from each one's middle the pointer took it. */
    private final Map<Orientation, Integer> grabOffsets = new EnumMap<>(Orientation.class);

    DividerDrag(CanvasPanes panes, PaneDivider divider) {
        this.panes = panes;
        this.divider = divider;
    }

    /** {@code event}'s point in the area. */
    private Point inArea(MouseEvent event) {
        return new Point(divider.getX() + event.getX(), divider.getY() + event.getY());
    }

    private int along(Orientation orientation, Point point) {
        return orientation == Orientation.HORIZONTAL ? point.y : point.x;
    }

    /** The dividers under {@code point}: this one, and the other one too at the crossing point. */
    private List<Orientation> dividersAt(Point point) {
        Orientation other = divider.orientation() == Orientation.HORIZONTAL
                ? Orientation.VERTICAL : Orientation.HORIZONTAL;
        boolean crossing = panes.divider(other).filter(found -> found.getBounds().contains(point)).isPresent();
        return crossing ? List.of(divider.orientation(), other) : List.of(divider.orientation());
    }

    private int middleOf(Orientation orientation) {
        Rectangle bounds = panes.dividerOf(orientation).getBounds();
        return orientation == Orientation.HORIZONTAL ? bounds.y + bounds.height / 2
                : bounds.x + bounds.width / 2;
    }

    @Override
    public void mouseMoved(MouseEvent event) {
        divider.setCursor(dividersAt(inArea(event)).size() > 1
                ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : PaneDivider.resizeCursor(divider.orientation()));
    }

    @Override
    public void mousePressed(MouseEvent event) {
        grabOffsets.clear();
        if (!SwingUtilities.isLeftMouseButton(event) || divider.getParent() != panes) {
            return;
        }
        Point point = inArea(event);
        for (Orientation orientation : dividersAt(point)) {
            grabOffsets.put(orientation, along(orientation, point) - middleOf(orientation));
        }
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        Point point = inArea(event);
        grabOffsets.forEach((orientation, offset) -> {
            if (panes.dividerFraction(orientation).isPresent()) {
                panes.moveDivider(orientation, panes.fractionFor(orientation, along(orientation, point) - offset));
            }
        });
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        if (grabOffsets.isEmpty()) {
            return;
        }
        Point point = inArea(event);
        List<Orientation> dragged = List.copyOf(grabOffsets.keySet());
        grabOffsets.clear();
        for (Orientation orientation : dragged) {
            int position = along(orientation, point);
            if (panes.dividerFraction(orientation).isPresent() && panes.isNearAnEdge(orientation, position)) {
                panes.removeDivider(orientation, position < CanvasPanes.EDGE_MARGIN ? Side.BEFORE : Side.AFTER);
            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent event) {
        if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1
                && divider.getParent() == panes) {
            dividersAt(inArea(event)).forEach(panes::removeDivider);
        }
    }
}
