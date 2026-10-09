package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.Set;
import java.util.function.Supplier;

/** Makes the wires of a net blink for about 1.5 s after a jump to a problem (spec §6.12). Repaint only. */
public final class NetBlink implements CanvasOverlay {

    static final int INTERVAL_MILLISECONDS = 250;
    static final int TOGGLES = 6;

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final BasicStroke STROKE = new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final int ALPHA = 170;

    private final Supplier<ColorScheme> scheme;
    private final Runnable repaint;
    private final Timer timer;
    private Set<ElementId> wires = Set.of();
    private int remainingToggles;
    private boolean lit;

    /** {@code scheme} is read on every paint, so the blink follows a scheme change while it runs. */
    public NetBlink(Supplier<ColorScheme> scheme, Runnable repaint) {
        this.scheme = scheme;
        this.repaint = repaint;
        this.timer = new Timer(INTERVAL_MILLISECONDS, event -> toggle());
    }

    public void start(Set<ElementId> netWires) {
        wires = Set.copyOf(netWires);
        remainingToggles = TOGGLES;
        lit = true;
        timer.restart();
        repaint.run();
    }

    public boolean isBlinking() {
        return remainingToggles > 0;
    }

    boolean isLit() {
        return lit;
    }

    /** One timer step: switch on/off; after {@link #TOGGLES} steps the blink ends. */
    void toggle() {
        if (remainingToggles <= 0) {
            return;
        }
        remainingToggles--;
        if (remainingToggles == 0) {
            lit = false;
            wires = Set.of();
            timer.stop();
        } else {
            lit = !lit;
        }
        repaint.run();
    }

    public void dispose() {
        timer.stop();
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        if (!lit || wires.isEmpty()) {
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
