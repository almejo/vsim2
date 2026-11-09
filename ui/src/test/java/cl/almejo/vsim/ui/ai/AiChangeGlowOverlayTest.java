package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** The glow over what an AI task changed: fading with animations, steady 1.5 s and then off without them. */
class AiChangeGlowOverlayTest {

    private static final long MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);

    private final CircuitDocument document = new CircuitDocument();
    private final Wire vertical = new Wire(document.newWireId(), new GridPoint(4, 1), new GridPoint(4, 7));
    private final AtomicLong clock = new AtomicLong();
    private final AtomicInteger repaints = new AtomicInteger();
    private final AtomicBoolean animations = new AtomicBoolean(true);

    private AiChangeGlowOverlay overlay() {
        document.add(vertical);
        return new AiChangeGlowOverlay(() -> ColorScheme.LIGHT, StandardComponentTypes.registry(),
                repaints::incrementAndGet, clock::get, animations::get);
    }

    private int alpha(AiChangeGlowOverlay overlay) {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.paint(graphics, new RenderState(document, Netlist.EMPTY, 0, SimulationSnapshot.EMPTY, Set.of(), 0));
        graphics.dispose();
        return new Color(image.getRGB(32, 32), true).getAlpha();
    }

    @Test
    void withAnimationsItFadesOutAndTheTimerStopsAt800Milliseconds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AiChangeGlowOverlay overlay = overlay();
            overlay.show(Set.of(vertical.id()));
            assertThat(alpha(overlay)).isEqualTo(AiChangeGlowOverlay.FULL_ALPHA);
            clock.set(475 * MILLISECOND);
            assertThat(alpha(overlay)).isEqualTo(70);
            clock.set(800 * MILLISECOND);
            overlay.tick();
            assertThat(overlay.isTimerRunning()).isFalse();
            assertThat(overlay.current()).isEmpty();
            assertThat(alpha(overlay)).isZero();
            overlay.dispose();
        });
    }

    @Test
    void withAnimationsOffItIsSteadyFor1500MillisecondsAndThenOffAtOnce() throws Exception {
        animations.set(false);
        SwingUtilities.invokeAndWait(() -> {
            AiChangeGlowOverlay overlay = overlay();
            overlay.show(Set.of(vertical.id()));
            clock.set(1_000 * MILLISECOND);
            overlay.tick();
            assertThat(alpha(overlay)).isEqualTo(AiChangeGlowOverlay.FULL_ALPHA);
            assertThat(overlay.current()).isPresent();
            clock.set(1_499 * MILLISECOND);
            overlay.tick();
            assertThat(alpha(overlay)).isEqualTo(AiChangeGlowOverlay.FULL_ALPHA);

            clock.set(1_500 * MILLISECOND);
            overlay.tick();

            assertThat(overlay.isTimerRunning()).isFalse();
            assertThat(overlay.current()).isEmpty();
            assertThat(alpha(overlay)).isZero();
            overlay.dispose();
        });
    }

    @Test
    void turningAnimationsOffWhileItFadesMakesItSteadyFromThatMoment() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AiChangeGlowOverlay overlay = overlay();
            overlay.show(Set.of(vertical.id()));
            clock.set(475 * MILLISECOND);

            animations.set(false);
            overlay.tick();

            assertThat(alpha(overlay)).isEqualTo(AiChangeGlowOverlay.FULL_ALPHA);
            assertThat(overlay.isTimerRunning()).isTrue();
            clock.set(1_500 * MILLISECOND);
            overlay.tick();
            assertThat(overlay.isTimerRunning()).isFalse();
            assertThat(alpha(overlay)).isZero();
            overlay.dispose();
        });
    }
}
