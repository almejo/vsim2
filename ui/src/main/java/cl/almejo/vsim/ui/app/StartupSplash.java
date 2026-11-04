package cl.almejo.vsim.ui.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * VSim's splash screen: an undecorated window centred on the screen under the pointer (the JVM's own splash centres
 * on the whole desktop, between two monitors). The image is {@code docs/logo/splash.html}; the version and the
 * «Cargando…» line are painted here so they follow the build and the language. The picture is composed before the
 * window shows (the first text drawn loads the fonts, which takes about a second), so it never opens empty. It stays at least
 * {@link #MINIMUM_TIME} on screen ({@link #awaitMinimumTime()} holds the first window back) and closes itself when
 * another window opens.
 */
final class StartupSplash {

    static final Duration MINIMUM_TIME = Duration.ofSeconds(3);

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupSplash.class);

    private static final int IMAGE_WIDTH = 640;
    private static final int IMAGE_HEIGHT = 400;
    private static final String[] IMAGE_RESOURCES = {"/splash/vsim2-splash.png", "/splash/vsim2-splash@2x.png"};
    private static final Color LOADING_COLOR = new Color(0x8A8F98);
    private static final Color VERSION_COLOR = new Color(0x5A5E66);
    /** The image's own background, behind everything until the picture is painted. */
    private static final Color BACKGROUND = new Color(0x1E1F22);

    private final Instant shownAt;

    private StartupSplash(Instant shownAt) {
        this.shownAt = shownAt;
    }

    /** Shows the splash (on the EDT, soon) unless the run is headless or the image is missing. */
    static Optional<StartupSplash> show(String version, String loading) {
        if (GraphicsEnvironment.isHeadless()) {
            return Optional.empty();
        }
        Optional<Image> picture = composePicture("v" + version, loading);
        if (picture.isEmpty()) {
            return Optional.empty();
        }
        SwingUtilities.invokeLater(() -> open(picture.get()));
        return Optional.of(new StartupSplash(Instant.now()));
    }

    /** How long the start still has to wait so the splash lasts {@code minimum}; zero once it has. */
    static Duration remaining(Instant shownAt, Instant now, Duration minimum) {
        Duration left = minimum.minus(Duration.between(shownAt, now));
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** Blocks until the splash has been up {@link #MINIMUM_TIME}. Call it off the EDT, before the first window. */
    void awaitMinimumTime() {
        Duration left = remaining(shownAt, Instant.now(), MINIMUM_TIME);
        if (left.isZero()) {
            return;
        }
        try {
            Thread.sleep(left);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Each image variant (1x, 2x) with the two lines already drawn at its own resolution. */
    private static Optional<Image> composePicture(String version, String loading) {
        List<Image> variants = new ArrayList<>();
        for (String resource : IMAGE_RESOURCES) {
            try (InputStream stream = StartupSplash.class.getResourceAsStream(resource)) {
                BufferedImage variant = stream == null ? null : ImageIO.read(stream);
                if (variant != null) {
                    variants.add(withTexts(variant, version, loading));
                }
            } catch (IOException exception) {
                LOGGER.warn("Cannot read the splash image {}", resource, exception);
            }
        }
        return variants.isEmpty() ? Optional.empty()
                : Optional.of(new BaseMultiResolutionImage(variants.toArray(Image[]::new)));
    }

    private static BufferedImage withTexts(BufferedImage image, String version, String loading) {
        BufferedImage composed = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = composed.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
            double scale = image.getWidth() / (double) IMAGE_WIDTH;
            graphics.scale(scale, scale);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            graphics.setColor(LOADING_COLOR);
            graphics.drawString(loading, 416, 344);
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.setColor(VERSION_COLOR);
            graphics.drawString(version, 606 - metrics.stringWidth(version), 376);
        } finally {
            graphics.dispose();
        }
        return composed;
    }

    private static void open(Image picture) {
        try {
            JWindow window = new JWindow();
            window.setBackground(BACKGROUND);
            window.setContentPane(new SplashPicture(picture));
            window.pack();
            Rectangle screen = screenUnderPointer().getBounds();
            window.setLocation(screen.x + (screen.width - window.getWidth()) / 2,
                    screen.y + (screen.height - window.getHeight()) / 2);
            closeWhenAnotherWindowOpens(window);
            window.setVisible(true);
        } catch (RuntimeException exception) {
            LOGGER.warn("Cannot show the splash screen", exception);
        }
    }

    private static GraphicsConfiguration screenUnderPointer() {
        PointerInfo pointer = MouseInfo.getPointerInfo();
        GraphicsDevice device = pointer == null ? null : pointer.getDevice();
        if (device == null) {
            device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        }
        return device.getDefaultConfiguration();
    }

    private static void closeWhenAnotherWindowOpens(JWindow splash) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        AWTEventListener listener = new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (event.getID() == WindowEvent.WINDOW_OPENED && event.getSource() != splash) {
                    toolkit.removeAWTEventListener(this);
                    splash.dispose();
                }
            }
        };
        toolkit.addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK);
    }

    /** The composed picture at 640 × 400 (its 2x variant on scaled screens). */
    private static final class SplashPicture extends JComponent {

        private final Image picture;

        SplashPicture(Image picture) {
            this.picture = picture;
            setOpaque(true);
            setBackground(BACKGROUND);
            setPreferredSize(new Dimension(IMAGE_WIDTH, IMAGE_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setColor(BACKGROUND);
                graphics2D.fillRect(0, 0, getWidth(), getHeight());
                graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics2D.drawImage(picture, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, null);
            } finally {
                graphics2D.dispose();
            }
        }
    }
}
