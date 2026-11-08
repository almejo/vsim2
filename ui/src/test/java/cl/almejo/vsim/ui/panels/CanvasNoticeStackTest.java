package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JRootPane;
import java.awt.Component;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Several notices at once over one area: newest at the bottom, a few at most, each expiring on its own. */
class CanvasNoticeStackTest {

    private final List<ManualFrameTicker> tickers = new ArrayList<>();
    private JRootPane frame;
    private JPanel canvasArea;
    private CanvasNoticeStack stack;
    private long now = 1_000;

    @BeforeEach
    void create() {
        frame = new JRootPane();
        canvasArea = new JPanel();
        frame.setContentPane(canvasArea);
        frame.setSize(800, 600);
        frame.getLayeredPane().setSize(800, 600);
        canvasArea.setSize(800, 600);
        stack = new CanvasNoticeStack(canvasArea, CanvasNotice.Position.BOTTOM, Duration.ofMillis(1500), 3, () -> {
            ManualFrameTicker ticker = new ManualFrameTicker();
            tickers.add(ticker);
            return ticker;
        }, () -> now, () -> false);
    }

    private void advance(long milliseconds) {
        now += milliseconds;
        for (ManualFrameTicker ticker : new ArrayList<>(tickers)) {
            ticker.advance(16);
        }
    }

    private List<CanvasNotice> shown() {
        List<CanvasNotice> found = new ArrayList<>();
        for (Component component : frame.getLayeredPane().getComponents()) {
            if (component instanceof CanvasNotice notice) {
                found.add(notice);
            }
        }
        return found;
    }

    private CanvasNotice shown(String text) {
        return shown().stream().filter(notice -> notice.text().equals(text)).findFirst().orElseThrow();
    }

    @Test
    void theyStackWithTheNewestAtTheBottom() {
        stack.push("One");
        stack.push("Two");
        stack.push("Three");

        assertThat(shown()).hasSize(3);
        assertThat(shown("Three").getY()).isGreaterThan(shown("Two").getY());
        assertThat(shown("Two").getY()).isGreaterThan(shown("One").getY());
    }

    @Test
    void aFourthPushesTheOldestOut() {
        stack.push("One");
        stack.push("Two");
        stack.push("Three");
        stack.push("Four");

        assertThat(shown()).extracting(CanvasNotice::text).containsExactlyInAnyOrder("Two", "Three", "Four");
        assertThat(shown("Four").getY()).isGreaterThan(shown("Three").getY());
    }

    @Test
    void eachExpiresOnItsOwnAndTheNewerOnesStayPut() {
        stack.push("One");
        advance(800);
        stack.push("Two");
        int before = shown("Two").getY();
        advance(800);

        assertThat(shown()).extracting(CanvasNotice::text).containsExactly("Two");
        assertThat(shown("Two").getY()).isEqualTo(before);
        advance(800);
        assertThat(shown()).isEmpty();
        assertThat(tickers).allSatisfy(ticker -> assertThat(ticker.isRunning()).isFalse());
    }

    @Test
    void clearTakesAllAway() {
        stack.push("One");
        stack.push("Two");
        stack.clear();
        assertThat(shown()).isEmpty();
    }

    @Test
    void aChipIsAtLeastTwiceAsHighAsANormalNotice() {
        CanvasNotice.show(canvasArea, "Normal", CanvasNotice.Position.TOP, Duration.ofSeconds(2),
                new ManualFrameTicker(), () -> now, false);
        int normalHeight = shown("Normal").getPreferredSize().height;

        stack.push(List.of("Ctrl", "Z"), true, "Deshacer");

        CanvasNotice chip = shown("Ctrl+Z · Deshacer");
        assertThat(chip.getPreferredSize().height).isGreaterThanOrEqualTo(2 * normalHeight);
        assertThat(chip.getFont().getSize2D()).isGreaterThan(1.5f * canvasArea.getFont().getSize2D());
        assertThat(chip.getFont().getSize2D()).as("scaled from the UI font").isEqualTo(
                canvasArea.getFont().getSize2D() * 1.8f, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    void theKeyBoxesComeBeforeTheNameAndTheNormalNoticeKeepsItsSize() {
        stack.push(List.of("Ctrl", "Z"), true, "Deshacer");
        CanvasNotice chip = shown("Ctrl+Z · Deshacer");
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(chip.getWidth(), chip.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        chip.paint(graphics);
        graphics.dispose();

        // a keycap has a lighter filled box: the first third of the chip has more key-box pixels than the last third
        int third = chip.getWidth() / 3;
        assertThat(boxPixels(image, 0, third)).isGreaterThan(boxPixels(image, 2 * third, chip.getWidth()));
        assertThat(chip.getWidth()).isGreaterThan(chip.getFontMetrics(chip.getFont()).stringWidth("Ctrl+Z · Deshacer"));
    }

    private static int boxPixels(java.awt.image.BufferedImage image, int fromX, int toX) {
        int count = 0;
        int background = image.getRGB(2, image.getHeight() / 2);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = fromX; x < toX; x++) {
                int pixel = image.getRGB(x, y);
                if (pixel != background && (pixel >>> 24) > 200 && pixel != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
