package cl.almejo.vsim.ui.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.SplashScreen;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The splash screen the JVM shows before {@link Main} runs ({@code SplashScreen-Image} in the jar's manifest,
 * {@code -splash:} for {@code ./gradlew :ui:run}). The image is {@code docs/logo/splash.html}; the version and the
 * «Cargando…» line are painted here so they follow the build and the language. It stays at least
 * {@link #MINIMUM_TIME} on screen: the JVM closes it when the first window shows, so the start waits the rest.
 */
final class StartupSplash {

    static final Duration MINIMUM_TIME = Duration.ofSeconds(3);

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupSplash.class);

    /** The image's own size; the overlay is drawn in these units and scaled to the splash's size. */
    private static final double IMAGE_WIDTH = 640;
    private static final Color VERSION_COLOR = new Color(0x4A5462);
    private static final Color LOADING_COLOR = new Color(0x56606D);

    private final SplashScreen splashScreen;
    private final Instant shownAt;

    private StartupSplash(SplashScreen splashScreen, Instant shownAt) {
        this.splashScreen = splashScreen;
        this.shownAt = shownAt;
    }

    /** The splash on screen, if the JVM shows one (never in headless runs or when started without it). */
    static Optional<StartupSplash> current() {
        if (GraphicsEnvironment.isHeadless()) {
            return Optional.empty();
        }
        try {
            SplashScreen splashScreen = SplashScreen.getSplashScreen();
            if (splashScreen == null) {
                return Optional.empty();
            }
            Instant shownAt = ProcessHandle.current().info().startInstant().orElseGet(Instant::now);
            return Optional.of(new StartupSplash(splashScreen, shownAt));
        } catch (RuntimeException exception) {
            LOGGER.warn("Cannot read the splash screen", exception);
            return Optional.empty();
        }
    }

    /** How long the start still has to wait so the splash lasts {@code minimum}; zero once it has. */
    static Duration remaining(Instant shownAt, Instant now, Duration minimum) {
        Duration left = minimum.minus(Duration.between(shownAt, now));
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** Paints «REV x.y» under «HOJA DE DATOS» and the loading line at the bottom left. Cosmetic, never fails. */
    void paintTexts(String version, String loading) {
        try {
            if (!splashScreen.isVisible()) {
                return;
            }
            Graphics2D graphics = splashScreen.createGraphics();
            try {
                Dimension size = splashScreen.getSize();
                double scale = size.width / IMAGE_WIDTH;
                graphics.scale(scale, scale);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
                FontMetrics metrics = graphics.getFontMetrics();
                String revision = "REV " + version;
                graphics.setColor(VERSION_COLOR);
                graphics.drawString(revision, 610 - metrics.stringWidth(revision), 59);
                graphics.setColor(LOADING_COLOR);
                graphics.drawString(loading, 30, 374);
            } finally {
                graphics.dispose();
            }
            splashScreen.update();
        } catch (RuntimeException exception) {
            LOGGER.warn("Cannot paint over the splash screen", exception);
        }
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
}
