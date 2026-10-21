package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.HighlightCache;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.canvas.ShortCircuitHighlight;

import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Slowly pulses the conflict color of shorted wires (spec §6.12) with a white veil of varying alpha.
 * Repaints every 100 ms, and only while the displayed snapshot has short circuits.
 */
public final class ShortCircuitPulse implements CanvasOverlay {

    static final int REPAINT_MILLISECONDS = 100;
    static final long PERIOD_NANOS = 1_600_000_000L;
    static final int MAXIMUM_ALPHA = 140;

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final BasicStroke STROKE = new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private final Supplier<HighlightCache> highlights;
    private final LongSupplier nanoClock;
    private final Timer timer;

    /** {@code highlights} is the canvas's ({@link cl.almejo.vsim.ui.canvas.CircuitCanvas#highlights()}). */
    public ShortCircuitPulse(HighlightCache highlights, Runnable repaint, LongSupplier nanoClock) {
        this(() -> highlights, repaint, nanoClock);
    }

    /**
     * {@code highlights} is asked on every paint: the active pane's cache when one pulse is painted in every pane of a
     * window (they all show the same document and snapshot).
     */
    public ShortCircuitPulse(Supplier<HighlightCache> highlights, Runnable repaint, LongSupplier nanoClock) {
        this.highlights = highlights;
        this.nanoClock = nanoClock;
        this.timer = new Timer(REPAINT_MILLISECONDS, event -> repaint.run());
    }

    public void showSnapshot(SimulationSnapshot snapshot) {
        boolean shorts = !snapshot.shortCircuits().isEmpty();
        if (shorts && !timer.isRunning()) {
            timer.start();
        } else if (!shorts && timer.isRunning()) {
            timer.stop();
        }
    }

    public boolean isPulsing() {
        return timer.isRunning();
    }

    HighlightCache highlights() {
        return highlights.get();
    }

    public void dispose() {
        timer.stop();
    }

    /** 0 at the start of each period, {@link #MAXIMUM_ALPHA} at its middle, smoothly (cosine). */
    static int alphaAt(long nanos) {
        double phase = Math.floorMod(nanos, PERIOD_NANOS) / (double) PERIOD_NANOS;
        return (int) Math.round(MAXIMUM_ALPHA * (0.5 - 0.5 * Math.cos(2 * Math.PI * phase)));
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        int alpha = alphaAt(nanoClock.getAsLong());
        if (alpha == 0) {
            return;
        }
        ShortCircuitHighlight highlight = highlights.get().shortCircuits(state);
        if (highlight.wires().isEmpty()) {
            return;
        }
        graphics.setStroke(STROKE);
        graphics.setColor(new Color(255, 255, 255, alpha));
        for (Wire wire : state.document().wires()) {
            if (highlight.wires().contains(wire.id())) {
                graphics.draw(new Line2D.Double(wire.start().x() * UNIT, wire.start().y() * UNIT,
                        wire.end().x() * UNIT, wire.end().y() * UNIT));
            }
        }
    }
}
