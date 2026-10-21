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
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The brief glow over what an AI task changed when it ends (spec §4: «changed parts glow briefly»): the step mode's
 * glow ({@link StepHighlight}: fully lit, then fading out within 800 ms) in the scheme's selection colour, a band over
 * each changed wire and a rounded outline around each changed part. Elements no longer in the document are skipped.
 * Its timer runs only while the glow is on. EDT only.
 */
final class AiChangeGlowOverlay implements CanvasOverlay {

    static final int TIMER_MILLISECONDS = 33;
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
    private final Timer timer;
    private StepHighlight glow;
    private boolean disposed;

    /** {@code scheme} is read on every paint; {@code repaint} runs on every change and fade step. */
    AiChangeGlowOverlay(Supplier<ColorScheme> scheme, ComponentTypeRegistry registry, Runnable repaint,
                        LongSupplier nanoClock) {
        this.scheme = scheme;
        this.registry = registry;
        this.repaint = repaint;
        this.nanoClock = nanoClock;
        this.timer = new Timer(TIMER_MILLISECONDS, event -> tick());
    }

    /** Lights {@code changed} from now; nothing when it is empty or after dispose. */
    void show(Set<ElementId> changed) {
        if (disposed || changed.isEmpty()) {
            return;
        }
        glow = new StepHighlight(changed, ANY_NETLIST, nanoClock.getAsLong());
        if (!timer.isRunning()) {
            timer.start();
        }
        repaint.run();
    }

    Optional<StepHighlight> current() {
        return Optional.ofNullable(glow);
    }

    boolean isTimerRunning() {
        return timer.isRunning();
    }

    /** One fade step: repaints, and once the glow is over drops it and stops the timer. */
    void tick() {
        if (glow == null || glow.isOver(nanoClock.getAsLong())) {
            glow = null;
            timer.stop();
        }
        repaint.run();
    }

    void dispose() {
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
        int alpha = (int) Math.round(FULL_ALPHA * shown.alpha(nanoClock.getAsLong()));
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
