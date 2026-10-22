package cl.almejo.vsim.ui.ai.present;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** The AI's subtitle in «Presentar» (IA.3 Task 7): where it goes, how it wraps and how it fades. */
class NarrationCaptionTest {

    private static final int PANE_WIDTH = 800;
    private static final int PANE_HEIGHT = 500;
    private static final long FADE_NANOS = TimeUnit.MILLISECONDS.toNanos(NarrationCaption.FADE_MILLISECONDS);

    private final BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
    private final Graphics2D graphics = image.createGraphics();
    private final FontMetrics metrics = graphics.getFontMetrics(new Font(Font.DIALOG, Font.PLAIN, 13));
    private final AtomicLong clock = new AtomicLong(1_000);
    private final AtomicBoolean animate = new AtomicBoolean(true);
    private final AtomicInteger repaints = new AtomicInteger();
    private final NarrationCaption caption = new NarrationCaption(() -> null, pane -> Optional.empty(),
            animate::get, repaints::incrementAndGet, clock::get);

    @AfterEach
    void close() {
        caption.dispose();
        graphics.dispose();
    }

    @Test
    void aShortLineSitsAtTheBottomCentreOfThePane() {
        NarrationCaption.Placement placement = NarrationCaption.place("Ahora copio el circuito", metrics,
                PANE_WIDTH, PANE_HEIGHT, Optional.empty()).orElseThrow();

        Rectangle band = placement.band();
        assertThat(placement.lines()).containsExactly("Ahora copio el circuito");
        assertThat(Math.abs(band.getCenterX() - PANE_WIDTH / 2.0)).isLessThanOrEqualTo(1);
        assertThat(band.getMaxY()).isEqualTo(PANE_HEIGHT - NarrationCaption.MARGIN);
        assertThat(band.width).isLessThanOrEqualTo(NarrationCaption.MAXIMUM_WIDTH);
    }

    @Test
    void aLongLineWrapsToTwoLinesAndEndsInAnEllipsis() {
        String longLine = "Ahora copio el circuito para hacer un componente nuevo que usaré en las etapas siguientes "
                + "como bloque, y así cada etapa queda como cómo se hizo en su propio marco del storyboard, "
                + "con su comprobación y su nota de prueba";

        NarrationCaption.Placement placement = NarrationCaption.place(longLine, metrics, PANE_WIDTH, PANE_HEIGHT,
                Optional.empty()).orElseThrow();

        assertThat(placement.lines()).hasSize(NarrationCaption.MAXIMUM_LINES);
        assertThat(placement.lines().getLast()).endsWith("…");
        for (String line : placement.lines()) {
            assertThat(metrics.stringWidth(line)).isLessThanOrEqualTo(placement.band().width);
        }
    }

    @Test
    void itNeverGoesUnderTheFloatingPanel() {
        List<Rectangle> panels = List.of(
                new Rectangle(PANE_WIDTH - 350, PANE_HEIGHT - 300, 340, 290), // bottom right
                new Rectangle(10, PANE_HEIGHT - 300, 340, 290), // bottom left
                new Rectangle(200, PANE_HEIGHT - 120, 400, 110), // bottom centre
                new Rectangle(PANE_WIDTH - 350, 10, 340, 200)); // top right: no conflict
        for (Rectangle panel : panels) {
            Optional<NarrationCaption.Placement> placement = NarrationCaption.place(
                    "Ahora copio el circuito para hacer un componente nuevo", metrics, PANE_WIDTH, PANE_HEIGHT,
                    Optional.of(panel));

            assertThat(placement).as("beside %s", panel).isPresent();
            assertThat(placement.get().band().intersects(panel)).as("beside %s", panel).isFalse();
            assertThat(new Rectangle(0, 0, PANE_WIDTH, PANE_HEIGHT).contains(placement.get().band())).isTrue();
        }
    }

    @Test
    void aPanelOverTheWholePaneLeavesNoRoomAndTheLineIsNotShown() {
        assertThat(NarrationCaption.place("Hola", metrics, PANE_WIDTH, PANE_HEIGHT,
                Optional.of(new Rectangle(0, 0, PANE_WIDTH, PANE_HEIGHT)))).isEmpty();
    }

    @Test
    void itFadesInAndOutUnlessReducedMotion() {
        caption.show("Hola");
        assertThat(caption.alpha()).isZero();
        assertThat(caption.isTimerRunning()).isTrue();
        clock.addAndGet(FADE_NANOS);
        assertThat(caption.alpha()).isEqualTo(1.0);

        caption.clear();
        clock.addAndGet(FADE_NANOS / 2);
        assertThat(caption.alpha()).isBetween(0.4, 0.6);
        clock.addAndGet(FADE_NANOS);
        caption.tick();
        assertThat(caption.text()).isEmpty();
        assertThat(caption.isTimerRunning()).isFalse();

        animate.set(false);
        caption.show("Sin animación");
        assertThat(caption.alpha()).isEqualTo(1.0);
        assertThat(caption.isTimerRunning()).isFalse();
        caption.clear();
        assertThat(caption.text()).isEmpty();
    }

    @Test
    void aNewLineReplacesTheShownOneInPlace() {
        animate.set(false);
        caption.show("Primera");
        caption.show("Segunda");

        assertThat(caption.text()).contains("Segunda");
        assertThat(repaints.get()).isGreaterThanOrEqualTo(2);
    }
}
