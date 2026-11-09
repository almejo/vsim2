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
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Slowly pulses the conflict color of shorted wires (spec §6.12) with a white veil of varying alpha.
 * Repaints every 100 ms, and only while the displayed snapshot has short circuits. With animations off there is no
 * veil and no timer: the wires keep the plain conflict colour, steady.
 */
public final class ShortCircuitPulse implements CanvasOverlay {

    static final int REPAINT_MILLISECONDS = 100;
    static final long PERIOD_NANOS = 1_600_000_000L;
    static final int MAXIMUM_ALPHA = 140;

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final BasicStroke STROKE = new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private final Supplier<HighlightCache> highlights;
    private final LongSupplier nanoClock;
    private final BooleanSupplier animations;
    private final Runnable repaint;
    private final Timer timer;
    private boolean shorts;

    /**
     * {@code highlights} is asked on every paint: the active pane's cache when one pulse is painted in every pane of a
     * window (they all show the same document and snapshot).
     */
    public ShortCircuitPulse(Supplier<HighlightCache> highlights, Runnable repaint, LongSupplier nanoClock,
                            BooleanSupplier animations) {
        this.highlights = highlights;
        this.nanoClock = nanoClock;
        this.animations = animations;
        this.repaint = repaint;
        this.timer = new Timer(REPAINT_MILLISECONDS, event -> tick());
    }

    public void showSnapshot(SimulationSnapshot snapshot) {
        shorts = !snapshot.shortCircuits().isEmpty();
        boolean pulsing = shorts && animations.getAsBoolean();
        if (pulsing && !timer.isRunning()) {
            timer.start();
        } else if (!pulsing && timer.isRunning()) {
            timer.stop();
        }
    }

    private void tick() {
        if (!animations.getAsBoolean()) {
            timer.stop();
        }
        repaint.run();
    }

    /** Re-reads the setting and the last snapshot: «Animaciones» changed while shorts were (or were not) showing. */
    public void refresh() {
        boolean pulsing = shorts && animations.getAsBoolean();
        if (pulsing && !timer.isRunning()) {
            timer.start();
        } else if (!pulsing && timer.isRunning()) {
            timer.stop();
        }
        repaint.run();
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
        // with «Animaciones: No» the shorted wires keep the plain conflict colour: no veil
        int alpha = animations.getAsBoolean() ? alphaAt(nanoClock.getAsLong()) : 0;
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
