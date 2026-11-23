package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.Collection;
import java.util.Set;

/**
 * Wires as the canvas and its overlays stroke them, in document pixels: the overlays that light some wires (a short
 * circuit's pulse, a step's highlight, the AI's change glow, a net's blink) draw them over the circuit with their own
 * stroke and colour.
 */
public final class WireStrokes {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;

    private WireStrokes() {
    }

    /** The wire's line, in document pixels. */
    public static Line2D line(Wire wire) {
        return new Line2D.Double(wire.start().x() * UNIT, wire.start().y() * UNIT, wire.end().x() * UNIT,
                wire.end().y() * UNIT);
    }

    /** Draws, with the current stroke and colour, the wires of {@code wires} whose id is in {@code lit}. */
    public static void drawLit(Graphics2D graphics, Collection<Wire> wires, Set<ElementId> lit) {
        for (Wire wire : wires) {
            if (lit.contains(wire.id())) {
                graphics.draw(line(wire));
            }
        }
    }
}
