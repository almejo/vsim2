package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;
import cl.almejo.vsim.ui.stepping.StepHighlight;

import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The brief glow over what an AI task changed when it ends (spec §4: «changed parts glow briefly»): the step mode's
 * glow ({@link StepHighlight}: fully lit, then fading out within 800 ms; with animations off steady for 1.5 s and then
 * out at once) in the scheme's selection colour, a band over each changed wire and a rounded outline around each
 * changed part. Elements no longer in the document are skipped. Its timer runs only while the glow is on, and
 * repaints only when what shows changes: every step of a fade, but a steady glow only when it goes out. Turning
 * animations off during a fade ends it at once (never brighter than it was). Also lights each step «Presentar»
 * applies (IA.2). EDT only.
 */
public final class AiChangeGlowOverlay implements CanvasOverlay {

    static final int TIMER_MILLISECONDS = 33;
    /** With animations off the glow is steady for this long and then goes out at once. */
    static final long STEADY_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
    /** Opacity of a fully lit glow (0–255), as the step mode's. */
    static final int FULL_ALPHA = 140;
    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final double PART_MARGIN = UNIT / 2;
    private static final BasicStroke WIRE_STROKE = new BasicStroke(7f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    private static final BasicStroke PART_STROKE = new BasicStroke(4f);
    /** The glow is not tied to one netlist: element ids stay valid across recompiles. */
    private static final long ANY_NETLIST = 0;

    private final Supplier<ColorScheme> scheme;
    private final ComponentTypeRegistry registry;
    private final Runnable repaint;
    private final LongSupplier nanoClock;
    private final BooleanSupplier animations;
    private final Timer timer;
    private StepHighlight glow;
    /** Whether the glow shown started with animations off: steady for {@link #STEADY_NANOS}, it does not fade. */
    private boolean steady;
    private boolean disposed;

    /** {@code scheme} is read on every paint; {@code repaint} runs on every change and fade step. */
    public AiChangeGlowOverlay(Supplier<ColorScheme> scheme, ComponentTypeRegistry registry, Runnable repaint,
                        LongSupplier nanoClock, BooleanSupplier animations) {
        this.scheme = scheme;
        this.registry = registry;
        this.repaint = repaint;
        this.nanoClock = nanoClock;
        this.animations = animations;
        this.timer = new Timer(TIMER_MILLISECONDS, event -> tick());
    }

    /** Lights {@code changed} from now; nothing when it is empty or after dispose. */
    public void show(Set<ElementId> changed) {
        if (disposed || changed.isEmpty()) {
            return;
        }
        glow = new StepHighlight(changed, ANY_NETLIST, nanoClock.getAsLong());
        steady = !animations.getAsBoolean();
        if (!timer.isRunning()) {
            timer.start();
        }
        repaint.run();
    }

    public Optional<StepHighlight> current() {
        return Optional.ofNullable(glow);
    }

    boolean isTimerRunning() {
        return timer.isRunning();
    }

    /**
     * One timer step: once the glow is over drops it and stops the timer. Repaints while it fades and when it goes
     * out; a steady glow looks the same until then.
     */
    void tick() {
        if (glow == null || isOver(glow)) {
            glow = null;
            timer.stop();
            repaint.run();
        } else if (!steady) {
            repaint.run();
        }
    }

    /**
     * Whether the glow is done: a steady one after its 1.5 s; a fading one after its fade, or as soon as animations
     * are off (the fade ends in its final state).
     */
    private boolean isOver(StepHighlight shown) {
        long now = nanoClock.getAsLong();
        if (steady) {
            return now - shown.startNanos() >= STEADY_NANOS;
        }
        return !animations.getAsBoolean() || shown.isOver(now);
    }

    public void dispose() {
        disposed = true;
        glow = null;
        timer.stop();
    }

    @Override
    public void paint(Graphics2D graphics, RenderState state) {
        StepHighlight shown = glow;
        if (shown == null) {
            return;
        }
        double lit = isOver(shown) ? 0 : steady ? 1 : shown.alpha(nanoClock.getAsLong());
        int alpha = (int) Math.round(FULL_ALPHA * lit);
        if (alpha <= 0) {
            return;
        }
        Set<ElementId> changed = shown.wires();
        graphics.setColor(PainterSupport.withAlpha(scheme.get().selection(), alpha));
        graphics.setStroke(WIRE_STROKE);
        for (Wire wire : state.document().wires()) {
            if (changed.contains(wire.id())) {
                graphics.draw(new Line2D.Double(wire.start().x() * UNIT, wire.start().y() * UNIT,
                        wire.end().x() * UNIT, wire.end().y() * UNIT));
            }
        }
        graphics.setStroke(PART_STROKE);
        for (Component component : state.document().components()) {
            if (changed.contains(component.id())) {
                Bounds bounds = DrawnBounds.of(component, registry);
                graphics.draw(new RoundRectangle2D.Double(bounds.x() * UNIT - PART_MARGIN,
                        bounds.y() * UNIT - PART_MARGIN, bounds.width() * UNIT + 2 * PART_MARGIN,
                        bounds.height() * UNIT + 2 * PART_MARGIN, UNIT, UNIT));
            }
        }
    }
}
