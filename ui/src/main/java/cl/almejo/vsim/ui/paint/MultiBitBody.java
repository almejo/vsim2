package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;

import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

/**
 * The frame the multi-bit Switch and LED share: the box of {@link MultiBitLayout}, the thick lead to the one bus pin
 * and the pin's square, with a cell painter for what goes in each bit. The body is always drawn upright in the
 * component's own frame; which side the pin is on is a parameter, never a turn of the drawing.
 */
final class MultiBitBody {

    private static final double BODY_CORNER = 4;

    private MultiBitBody() {
    }

    /** Paints one bit; {@code graphics} is already in the body's frame (pixels from its top-left corner). */
    @FunctionalInterface
    interface CellPainter {
        void paint(Graphics2D graphics, MultiBitLayout.BitCell cell);
    }

    static void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, ColorScheme scheme,
                      CellPainter cellPainter) {
        int width = MultiBitLayout.widthOf(type);
        String side = MultiBitLayout.sideOf(type, parameters);
        paintLead(graphics, type.pin(0), side, scheme);
        GridSize box = MultiBitLayout.boxFor(width);
        GridPoint origin = MultiBitLayout.bodyOrigin(width, side);
        Graphics2D bodyGraphics = (Graphics2D) graphics.create();
        try {
            bodyGraphics.translate(origin.x() * PainterSupport.UNIT, origin.y() * PainterSupport.UNIT);
            PainterSupport.body(bodyGraphics, new RoundRectangle2D.Double(0, 0, box.width() * PainterSupport.UNIT,
                    box.height() * PainterSupport.UNIT, BODY_CORNER, BODY_CORNER), scheme);
            for (MultiBitLayout.BitCell cell : MultiBitLayout.cells(width)) {
                cellPainter.paint(bodyGraphics, cell);
            }
        } finally {
            bodyGraphics.dispose();
        }
        PainterSupport.pinDots(graphics, type, scheme);
    }

    /** The lead runs from the middle of the body's side to the pin, {@link MultiBitLayout#LEAD_UNITS} units long. */
    private static void paintLead(Graphics2D graphics, PinSpec pin, String side, ColorScheme scheme) {
        double pinX = pin.position().x() * PainterSupport.UNIT;
        double pinY = pin.position().y() * PainterSupport.UNIT;
        double lead = MultiBitLayout.LEAD_UNITS * PainterSupport.UNIT;
        double startX = switch (side) {
            case MultiBitLayout.RIGHT -> pinX - lead;
            case MultiBitLayout.LEFT -> pinX + lead;
            default -> pinX;
        };
        double startY = switch (side) {
            case MultiBitLayout.BOTTOM -> pinY - lead;
            case MultiBitLayout.TOP -> pinY + lead;
            default -> pinY;
        };
        PainterSupport.pinLead(graphics, pin, startX, startY, pinX, pinY, scheme);
    }
}
