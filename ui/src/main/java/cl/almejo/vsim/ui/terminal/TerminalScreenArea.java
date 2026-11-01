package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.terminal.CellPosition;
import cl.almejo.vsim.core.terminal.TerminalFontSize;
import cl.almejo.vsim.core.terminal.TerminalGeometry;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.Optional;

/**
 * Where a Terminal's screen and its cells are on the canvas: the screen starts one margin in from the left and one
 * margin below the title bar ({@link TerminalGeometry}), and each cell has the fixed size of the part's «Tamaño de
 * letra» ({@link TerminalFontSize}), as the painter lays them out. A UART 16550's integrated screen starts right of
 * its register table ({@link UartTypes#SCREEN_LEFT_UNITS}); a UART with TX and RX pins has none. A double click on the
 * screen opens the window; the cell under the mouse gives the tooltip.
 */
public final class TerminalScreenArea {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final Point2D TERMINAL_SCREEN = new Point2D.Double(TerminalGeometry.MARGIN_UNITS * UNIT,
            (TerminalGeometry.TITLE_BAR_UNITS + TerminalGeometry.MARGIN_UNITS) * UNIT);
    private static final Point2D UART_SCREEN = new Point2D.Double(UartTypes.SCREEN_LEFT_UNITS * UNIT,
            (UartTypes.TITLE_BAR_UNITS + UartTypes.MARGIN_UNITS) * UNIT);

    private TerminalScreenArea() {
    }

    /** Whether {@code gridPosition} (grid units) is on a cell of the Terminal {@code component}'s screen. */
    public static boolean isOnScreen(ComponentType type, Component component, Point2D gridPosition) {
        return cellAt(type, component, gridPosition).isPresent();
    }

    /** The cell of {@code component}'s screen under {@code gridPosition}; empty off the screen or without one. */
    public static Optional<CellPosition> cellAt(ComponentType type, Component component, Point2D gridPosition) {
        Map<String, Object> resolved = type.resolveParameters(component.parameters());
        Optional<Point2D> screen = screenCorner(component, resolved);
        if (screen.isEmpty()) {
            return Optional.empty();
        }
        TerminalFontSize fontSize = TerminalTypes.fontSize(resolved);
        Point2D local = local(component, gridPosition);
        double column = Math.floor((local.getX() - screen.get().getX()) / fontSize.cellWidth());
        double row = Math.floor((local.getY() - screen.get().getY()) / fontSize.cellHeight());
        int rows = (Integer) resolved.get(TerminalTypes.ROWS);
        int columns = (Integer) resolved.get(TerminalTypes.COLUMNS);
        if (row < 0 || column < 0 || row >= rows || column >= columns) {
            return Optional.empty();
        }
        return Optional.of(new CellPosition((int) row, (int) column));
    }

    /** The top-left corner of the screen in the part's own pixels; empty for a UART with TX and RX pins. */
    private static Optional<Point2D> screenCorner(Component component, Map<String, Object> resolved) {
        if (!component.typeId().equals(StandardComponentTypes.UART_16550)) {
            return Optional.of(TERMINAL_SCREEN);
        }
        return UartTypes.isIntegrated(resolved) ? Optional.of(UART_SCREEN) : Optional.empty();
    }

    /** {@code gridPosition} in the part's own pixels, before its rotation, as the painter draws. */
    private static Point2D local(Component component, Point2D gridPosition) {
        AffineTransform placed = new AffineTransform();
        placed.translate(component.position().x() * UNIT, component.position().y() * UNIT);
        placed.rotate(component.rotation().quarterTurns() * Math.PI / 2);
        try {
            return placed.inverseTransform(new Point2D.Double(gridPosition.getX() * UNIT,
                    gridPosition.getY() * UNIT), null);
        } catch (NoninvertibleTransformException exception) {
            throw new IllegalStateException("a rotation and a translation are always invertible", exception);
        }
    }
}
