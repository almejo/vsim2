package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.Set;
import java.util.function.Supplier;

/** Makes the wires of a net blink for about 1.5 s after a jump to a problem (spec §6.12). Repaint only. */
public final class NetBlink implements CanvasOverlay {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final BasicStroke STROKE = new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final int ALPHA = 170;

    private final Supplier<ColorScheme> scheme;
    private final Blink blink;
    private Set<ElementId> wires = Set.of();

    /** {@code scheme} is read on every paint, so the blink follows a scheme change while it runs. */
    public NetBlink(Supplier<ColorScheme> scheme, Runnable repaint) {
        this.scheme = scheme;
        this.blink = new Blink(repaint);
    }

    public void start(Set<ElementId> netWires) {
        wires = Set.copyOf(netWires);
        blink.start();
    }

    /** Ends a running blink at once, with nothing lit. */
    public void stop() {
        wires = Set.of();
        blink.stop();
    }

    public boolean isBlinking() {
        return blink.isBlinking();
    }

    boolean isLit() {
        return blink.isLit();
    }

    /** For tests: the on/off steps. */
    Blink steps() {
        return blink;
    }

    public void dispose() {
        blink.dispose();
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        if (!blink.isLit() || wires.isEmpty()) {
            return;
        }
        Color color = scheme.get().selection();
        graphics.setStroke(STROKE);
        graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), ALPHA));
        for (Wire wire : state.document().wires()) {
            if (wires.contains(wire.id())) {
                graphics.draw(new Line2D.Double(wire.start().x() * UNIT, wire.start().y() * UNIT,
                        wire.end().x() * UNIT, wire.end().y() * UNIT));
            }
        }
    }
}
