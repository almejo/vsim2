package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.AppearanceTestSupport;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StepHighlightOverlayTest {

    private static final long MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);
    private static final long TOKEN = 3;

    private final CircuitDocument document = new CircuitDocument();
    private final Wire vertical = new Wire(document.newWireId(), new GridPoint(4, 1), new GridPoint(4, 7));
    private final Wire horizontal = new Wire(document.newWireId(), new GridPoint(1, 9), new GridPoint(9, 9));
    private final AtomicLong clock = new AtomicLong();
    private final AtomicInteger repaints = new AtomicInteger();

    /** The pixel on the vertical wire (32, 32) and on the horizontal one (40, 72), painted at {@code token}. */
    private Color pixel(StepHighlightOverlay overlay, int x, int y, long token) {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.paint(graphics, new RenderState(document, Netlist.EMPTY, token, SimulationSnapshot.EMPTY, Set.of(), 0));
        graphics.dispose();
        return new Color(image.getRGB(x, y), true);
    }

    private Color onVertical(StepHighlightOverlay overlay) {
        return pixel(overlay, 32, 32, TOKEN);
    }

    private StepHighlight glow(Wire wire) {
        return new StepHighlight(Set.of(wire.id()), TOKEN, clock.get());
    }

    private static void onEventThread(Runnable steps) throws Exception {
        try {
            SwingUtilities.invokeAndWait(steps);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof AssertionError error) {
                throw error;
            }
            throw exception;
        }
    }

    @Test
    void theBandIsFullThenFadesAndTheTimerStopsWhenItIsOver() throws Exception {
        document.add(vertical);
        onEventThread(() -> {
            StepHighlightOverlay overlay = new StepHighlightOverlay(() -> ColorScheme.LIGHT, repaints::incrementAndGet,
                    clock::get);
            assertThat(onVertical(overlay).getAlpha()).isZero();
            assertThat(overlay.isTimerRunning()).isFalse();

            overlay.show(glow(vertical));
            assertThat(overlay.isTimerRunning()).isTrue();
            assertThat(repaints).hasValue(1);
            assertThat(onVertical(overlay).getAlpha()).isEqualTo(StepHighlightOverlay.FULL_ALPHA);

            clock.set(475 * MILLISECOND);
            overlay.tick();
            assertThat(onVertical(overlay).getAlpha()).isEqualTo(70);
            assertThat(overlay.isTimerRunning()).isTrue();

            clock.set(800 * MILLISECOND);
            overlay.tick();
            assertThat(overlay.isTimerRunning()).isFalse();
            assertThat(overlay.current()).isEmpty();
            assertThat(onVertical(overlay).getAlpha()).isZero();
            assertThat(repaints).hasValue(3);
            overlay.dispose();
        });
    }

    @Test
    void aNewStepRestartsTheGlowWithItsOwnWires() throws Exception {
        document.add(vertical);
        document.add(horizontal);
        onEventThread(() -> {
            StepHighlightOverlay overlay = new StepHighlightOverlay(() -> ColorScheme.LIGHT, repaints::incrementAndGet,
                    clock::get);
            overlay.show(glow(vertical));
            clock.set(600 * MILLISECOND);

            overlay.show(glow(horizontal));

            assertThat(onVertical(overlay).getAlpha()).isZero();
            assertThat(pixel(overlay, 40, 72, TOKEN).getAlpha()).isEqualTo(StepHighlightOverlay.FULL_ALPHA);
            overlay.clear();
            assertThat(overlay.isTimerRunning()).isFalse();
            assertThat(pixel(overlay, 40, 72, TOKEN).getAlpha()).isZero();
            overlay.dispose();
        });
    }

    @Test
    void aGlowOfAnOlderNetlistIsNotPainted() throws Exception {
        document.add(vertical);
        onEventThread(() -> {
            StepHighlightOverlay overlay = new StepHighlightOverlay(() -> ColorScheme.LIGHT, repaints::incrementAndGet,
                    clock::get);
            overlay.show(glow(vertical));

            assertThat(pixel(overlay, 32, 32, TOKEN + 1).getAlpha()).isZero();
            overlay.dispose();
        });
    }

    @Test
    void eachPaintUsesTheSchemeInUseAtThatMoment() throws Exception {
        document.add(vertical);
        ColorScheme magenta = AppearanceTestSupport.withSelection(ColorScheme.LIGHT, Color.MAGENTA);
        AtomicReference<ColorScheme> scheme = new AtomicReference<>(ColorScheme.LIGHT);
        onEventThread(() -> {
            StepHighlightOverlay overlay = new StepHighlightOverlay(scheme::get, repaints::incrementAndGet,
                    clock::get);
            StepHighlightOverlay lightReference = new StepHighlightOverlay(() -> ColorScheme.LIGHT,
                    repaints::incrementAndGet, clock::get);
            StepHighlightOverlay magentaReference = new StepHighlightOverlay(() -> magenta,
                    repaints::incrementAndGet, clock::get);
            for (StepHighlightOverlay shown : List.of(overlay, lightReference, magentaReference)) {
                shown.show(glow(vertical));
            }
            assertThat(onVertical(overlay)).isEqualTo(onVertical(lightReference));

            scheme.set(magenta);
            assertThat(onVertical(overlay)).isEqualTo(onVertical(magentaReference))
                    .isNotEqualTo(onVertical(lightReference));
            for (StepHighlightOverlay shown : List.of(overlay, lightReference, magentaReference)) {
                shown.dispose();
            }
        });
    }

    @Test
    void afterDisposeNothingStartsOrPaintsAgain() throws Exception {
        document.add(vertical);
        onEventThread(() -> {
            StepHighlightOverlay overlay = new StepHighlightOverlay(() -> ColorScheme.LIGHT, repaints::incrementAndGet,
                    clock::get);
            overlay.show(glow(vertical));
            overlay.dispose();
            assertThat(overlay.isTimerRunning()).isFalse();

            overlay.show(glow(vertical));

            assertThat(overlay.isTimerRunning()).isFalse();
            assertThat(overlay.current()).isEmpty();
            assertThat(onVertical(overlay).getAlpha()).isZero();
        });
    }
}
