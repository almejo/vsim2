package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;

import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Paints the glow of the last «Avanzar» (M6.1-B): a translucent 7 px band in the scheme's selection color over each
 * changed wire, its opacity following {@link StepHighlight#alpha}. Its timer runs only while a glow is on and never
 * starts again after {@link #dispose()}. EDT only.
 */
public final class StepHighlightOverlay implements CanvasOverlay {

    static final int TIMER_MILLISECONDS = 33;
    /** Opacity of a fully lit band (0–255). */
    static final int FULL_ALPHA = 140;
    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final BasicStroke STROKE = new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private final Supplier<ColorScheme> scheme;
    private final Runnable repaint;
    private final LongSupplier nanoClock;
    private final Timer timer;
    private StepHighlight highlight;
    private boolean disposed;

    /** {@code scheme} is read on every paint; {@code repaint} runs on every change and fade step. */
    public StepHighlightOverlay(Supplier<ColorScheme> scheme, Runnable repaint, LongSupplier nanoClock) {
        this.scheme = scheme;
        this.repaint = repaint;
        this.nanoClock = nanoClock;
        this.timer = new Timer(TIMER_MILLISECONDS, event -> tick());
    }

    /** Replaces the current glow (a new «Avanzar» restarts it with its own wires). Ignored after dispose. */
    public void show(StepHighlight next) {
        if (disposed) {
            return;
        }
        highlight = next;
        if (!timer.isRunning()) {
            timer.start();
        }
        repaint.run();
    }

    /** Ends the glow at once. */
    public void clear() {
        highlight = null;
        timer.stop();
        repaint.run();
    }

    public Optional<StepHighlight> current() {
        return Optional.ofNullable(highlight);
    }

    boolean isTimerRunning() {
        return timer.isRunning();
    }

    /** One fade step: repaints, and once the glow is over drops it and stops the timer. */
    void tick() {
        if (highlight == null || highlight.isOver(nanoClock.getAsLong())) {
            highlight = null;
            timer.stop();
        }
        repaint.run();
    }

    public void dispose() {
        disposed = true;
        highlight = null;
        timer.stop();
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        StepHighlight shown = highlight;
        if (shown == null || shown.wires().isEmpty() || !shown.matches(state.netlistToken())) {
            return;
        }
        int alpha = (int) Math.round(FULL_ALPHA * shown.alpha(nanoClock.getAsLong()));
        if (alpha <= 0) {
            return;
        }
        graphics.setStroke(STROKE);
        graphics.setColor(PainterSupport.withAlpha(scheme.get().selection(), alpha));
        for (Wire wire : state.document().wires()) {
            if (shown.wires().contains(wire.id())) {
                graphics.draw(new Line2D.Double(wire.start().x() * UNIT, wire.start().y() * UNIT,
                        wire.end().x() * UNIT, wire.end().y() * UNIT));
            }
        }
    }
}
