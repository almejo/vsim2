package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JRootPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** The notice over a canvas: shown for its duration, then gone; hideable early. Never a shown window. */
class CanvasNoticeTest {

    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private JRootPane frame;
    private JPanel canvasArea;

    @BeforeEach
    void create() {
        frame = new JRootPane();
        canvasArea = new JPanel();
        frame.setContentPane(canvasArea);
        frame.setSize(800, 600);
        frame.doLayout();
        frame.getLayeredPane().setSize(800, 600);
        canvasArea.setSize(800, 600);
    }

    private int noticesShown() {
        int count = 0;
        for (Component component : frame.getLayeredPane().getComponents()) {
            if (component instanceof CanvasNotice) {
                count++;
            }
        }
        return count;
    }

    private CanvasNotice notice() {
        for (Component component : frame.getLayeredPane().getComponents()) {
            if (component instanceof CanvasNotice notice) {
                return notice;
            }
        }
        throw new AssertionError("no notice shown");
    }

    @Test
    void showsAtOnceAndHidesAfterItsDuration() {
        CanvasNotice.show(canvasArea, "Hello", CanvasNotice.Position.TOP, Duration.ofSeconds(2), ticker,
                ticker::now, true);
        assertThat(noticesShown()).isEqualTo(1);
        ticker.advance(1_900);
        assertThat(noticesShown()).isEqualTo(1);
        ticker.advance(200);
        assertThat(noticesShown()).isZero();
        assertThat(ticker.isRunning()).as("the ticker stops with the notice").isFalse();
    }

    @Test
    void turningAnimationsOffWhileItFadesInShowsItWholeAtTheNextFrame() {
        AtomicBoolean animations = new AtomicBoolean(true);
        CanvasNotice.show(canvasArea, "Hello", CanvasNotice.Position.TOP, Duration.ofSeconds(2), ticker,
                ticker::now, animations::get);
        ticker.advance(16);
        assertThat(notice().opacity()).isLessThan(1f);

        animations.set(false);
        ticker.advance(16);

        assertThat(notice().opacity()).isEqualTo(1f);
    }

    @Test
    void isCentredAtTheTopOrTheBottomOfTheArea() {
        Runnable hideTop = CanvasNotice.show(canvasArea, "Hello", CanvasNotice.Position.TOP,
                Duration.ofSeconds(2), ticker, ticker::now, false);
        CanvasNotice top = notice();
        assertThat(top.getY()).isLessThan(100);
        assertThat(top.getX() + top.getWidth() / 2).isBetween(395, 405);
        hideTop.run();
        CanvasNotice.show(canvasArea, "Hello", CanvasNotice.Position.BOTTOM, Duration.ofSeconds(2), ticker,
                ticker::now, false);
        assertThat(notice().getY()).isGreaterThan(400);
    }

    @Test
    void theReturnedRunnableHidesItEarlyAndTwiceIsHarmless() {
        Runnable hide = CanvasNotice.show(canvasArea, "Hello", CanvasNotice.Position.TOP, Duration.ofSeconds(2),
                ticker, ticker::now, true);
        ticker.advance(300);
        hide.run();
        hide.run();
        assertThat(noticesShown()).isZero();
        assertThat(ticker.isRunning()).isFalse();
    }

    @Test
    void theColoursAreReadFromTheThemeEachTimeItPaints() {
        Color oldBackground = UIManager.getColor("ToolTip.background");
        Color oldForeground = UIManager.getColor("ToolTip.foreground");
        try {
            CanvasNotice.show(canvasArea, "Hello", CanvasNotice.Position.TOP, Duration.ofSeconds(2), ticker,
                    ticker::now, false);
            CanvasNotice notice = notice();
            UIManager.put("ToolTip.background", new Color(200, 0, 0));
            UIManager.put("ToolTip.foreground", Color.WHITE);
            Color first = cornerlessPixel(notice);
            UIManager.put("ToolTip.background", new Color(0, 0, 200));
            Color second = cornerlessPixel(notice);
            assertThat(first.getRed()).isGreaterThan(first.getBlue());
            assertThat(second.getBlue()).isGreaterThan(second.getRed());
        } finally {
            UIManager.put("ToolTip.background", oldBackground);
            UIManager.put("ToolTip.foreground", oldForeground);
        }
    }

    private static Color cornerlessPixel(CanvasNotice notice) {
        BufferedImage image = new BufferedImage(notice.getWidth(), notice.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        notice.paint(graphics);
        graphics.dispose();
        return new Color(image.getRGB(notice.getWidth() / 2, 2), true);
    }

    @Test
    void aNewNoticeReplacesTheOneStillShownOverTheSameArea() {
        CanvasNotice.show(canvasArea, "One", CanvasNotice.Position.TOP, Duration.ofSeconds(2), ticker,
                ticker::now, false);
        CanvasNotice.show(canvasArea, "Two", CanvasNotice.Position.TOP, Duration.ofSeconds(2), ticker,
                ticker::now, false);
        assertThat(noticesShown()).isEqualTo(1);
        assertThat(notice().text()).isEqualTo("Two");
    }

    @Test
    void aReplacedNoticeStopsItsOwnTicker() {
        ManualFrameTicker other = new ManualFrameTicker();
        CanvasNotice.show(canvasArea, "One", CanvasNotice.Position.TOP, Duration.ofSeconds(2), ticker,
                ticker::now, false);
        CanvasNotice.show(canvasArea, "Two", CanvasNotice.Position.TOP, Duration.ofSeconds(2), other,
                other::now, false);
        assertThat(ticker.isRunning()).as("the replaced notice's ticker").isFalse();
        assertThat(other.isRunning()).isTrue();
    }
}
