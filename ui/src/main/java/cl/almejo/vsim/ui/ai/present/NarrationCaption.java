package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.ui.ai.AiColors;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * What the AI says in «Presentar» (IA.3, {@code vsim_say}) as a subtitle: a rounded, translucent band at the bottom
 * centre of the active pane, at most {@link #MAXIMUM_LINES} lines (a longer line ends in «…»), never under the
 * floating panel (it moves beside it, above it, or to the top, else it is not shown). It stays until the next line or
 * until it is cleared, and fades in and out unless the user asked for reduced motion. Colours from the theme, read at
 * paint time; editor chrome, never on paper. EDT only.
 */
final class NarrationCaption {

    /** Pixels the band keeps from the pane's edges and from the floating panel. */
    static final int MARGIN = 16;
    /** The widest band, so a line reads as a subtitle and not as a banner. */
    static final int MAXIMUM_WIDTH = 560;
    static final int MAXIMUM_LINES = 2;
    static final int FADE_MILLISECONDS = 250;
    /** The narrowest band beside the panel before it tries above the panel. */
    private static final int MINIMUM_WIDTH = 200;
    private static final int PADDING_X = 14;
    private static final int PADDING_Y = 7;
    private static final int LINE_GAP = 2;
    private static final int ARC = 14;
    private static final float BACKGROUND_OPACITY = 0.88f;
    private static final int TIMER_MILLISECONDS = 33;
    private static final long FADE_NANOS = TimeUnit.MILLISECONDS.toNanos(FADE_MILLISECONDS);
    private static final String ELLIPSIS = "…";

    /** The band in pane pixels and the lines it holds. */
    record Placement(Rectangle band, List<String> lines) {
    }

    private final Supplier<CircuitCanvas> activePane;
    private final Function<CircuitCanvas, Optional<Rectangle>> covered;
    private final BooleanSupplier animate;
    private final BooleanSupplier showing;
    private final Runnable repaint;
    private final LongSupplier nanoClock;
    private final Timer timer;
    private String text;
    private long shownAt;
    /** When the line started fading out; {@code -1} while it is not leaving. */
    private long leavingSince = -1;
    private boolean disposed;

    /**
     * @param activePane the pane the subtitle shows in
     * @param covered    the part of a pane the floating panel covers, in its pixels (empty while hidden)
     * @param animate    false when the user asked for reduced motion (read at each change)
     * @param repaint    repaints every pane
     */
    NarrationCaption(Supplier<CircuitCanvas> activePane, Function<CircuitCanvas, Optional<Rectangle>> covered,
                     BooleanSupplier animate, Runnable repaint, LongSupplier nanoClock) {
        this(activePane, covered, animate, () -> true, repaint, nanoClock);
    }

    /** @param showing false while the window does not present (the user left «Presentar»): nothing is painted */
    NarrationCaption(Supplier<CircuitCanvas> activePane, Function<CircuitCanvas, Optional<Rectangle>> covered,
                     BooleanSupplier animate, BooleanSupplier showing, Runnable repaint, LongSupplier nanoClock) {
        this.activePane = Objects.requireNonNull(activePane, "activePane");
        this.covered = Objects.requireNonNull(covered, "covered");
        this.animate = Objects.requireNonNull(animate, "animate");
        this.showing = Objects.requireNonNull(showing, "showing");
        this.repaint = Objects.requireNonNull(repaint, "repaint");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.timer = new Timer(TIMER_MILLISECONDS, event -> tick());
    }

    /** Shows {@code line}, replacing the one there was in place (a new one fades in). */
    void show(String line) {
        if (disposed) {
            return;
        }
        boolean replacing = text != null && leavingSince < 0;
        text = Objects.requireNonNull(line, "line");
        leavingSince = -1;
        if (!replacing || !animate.getAsBoolean()) {
            shownAt = animate.getAsBoolean() ? nanoClock.getAsLong() : Long.MIN_VALUE;
        }
        startFadingIfNeeded();
        repaint.run();
    }

    /** Takes the line away (fading out unless reduced motion). */
    void clear() {
        if (text == null || leavingSince >= 0) {
            return;
        }
        if (animate.getAsBoolean() && !disposed) {
            leavingSince = nanoClock.getAsLong();
            startFadingIfNeeded();
        } else {
            drop();
        }
        repaint.run();
    }

    private void drop() {
        text = null;
        leavingSince = -1;
        timer.stop();
    }

    private void startFadingIfNeeded() {
        if ((alpha() < 1.0 || leavingSince >= 0) && !timer.isRunning()) {
            timer.start();
        }
    }

    /** One fade step: repaints, drops a line that faded out, stops the timer when nothing fades. */
    void tick() {
        long now = nanoClock.getAsLong();
        if (leavingSince >= 0 && now - leavingSince >= FADE_NANOS) {
            drop();
        } else if (leavingSince < 0 && alpha() >= 1.0) {
            timer.stop();
        }
        repaint.run();
    }

    /** The subtitle's opacity now, 0 to 1. */
    double alpha() {
        if (text == null) {
            return 0;
        }
        long now = nanoClock.getAsLong();
        if (leavingSince >= 0) {
            return Math.max(0, 1 - (now - leavingSince) / (double) FADE_NANOS);
        }
        if (shownAt == Long.MIN_VALUE) {
            return 1;
        }
        return Math.min(1, Math.max(0, (now - shownAt) / (double) FADE_NANOS));
    }

    boolean isTimerRunning() {
        return timer.isRunning();
    }

    /** The line shown (also while it fades out). */
    Optional<String> text() {
        return Optional.ofNullable(text);
    }

    void dispose() {
        disposed = true;
        drop();
    }

    // Placement

    /** Where the subtitle goes in {@code pane}: only the active pane, only while there is a line. */
    Optional<Placement> placementIn(CircuitCanvas pane, Graphics2D measuring) {
        if (text == null || !showing.getAsBoolean() || pane != activePane.get() || pane.getWidth() <= 0
                || pane.getHeight() <= 0) {
            return Optional.empty();
        }
        return place(text, measuring.getFontMetrics(font()), pane.getWidth(), pane.getHeight(), covered.apply(pane));
    }

    /**
     * The band for {@code line} in a pane of the given size: at the bottom centre; when the floating panel
     * ({@code obstacle}, pane pixels) is in the way, beside it at the bottom, else above it, else at the top centre;
     * empty when none of those is clear of it.
     */
    static Optional<Placement> place(String line, FontMetrics metrics, int paneWidth, int paneHeight,
                                     Optional<Rectangle> obstacle) {
        int widest = Math.min(MAXIMUM_WIDTH, paneWidth - 2 * MARGIN);
        if (widest <= 2 * PADDING_X) {
            return Optional.empty();
        }
        Placement bottom = band(line, metrics, widest, 0, paneWidth, Bottom.ofPane(paneHeight));
        if (clear(bottom, obstacle)) {
            return Optional.of(bottom);
        }
        Rectangle panel = obstacle.orElseThrow();
        int leftSpan = panel.x - 2 * MARGIN;
        int rightSpan = paneWidth - panel.x - panel.width - 2 * MARGIN;
        int span = Math.min(MAXIMUM_WIDTH, Math.max(leftSpan, rightSpan));
        if (span >= MINIMUM_WIDTH) {
            int spanStart = leftSpan >= rightSpan ? 0 : panel.x + panel.width;
            int spanEnd = leftSpan >= rightSpan ? panel.x : paneWidth;
            Placement beside = band(line, metrics, span, spanStart, spanEnd, Bottom.ofPane(paneHeight));
            if (clear(beside, obstacle)) {
                return Optional.of(beside);
            }
        }
        Placement above = band(line, metrics, widest, 0, paneWidth, Bottom.at(panel.y - MARGIN));
        if (above.band().y >= MARGIN && clear(above, obstacle)) {
            return Optional.of(above);
        }
        Placement top = band(line, metrics, widest, 0, paneWidth, Bottom.top());
        if (top.band().getMaxY() <= paneHeight - MARGIN && clear(top, obstacle)) {
            return Optional.of(top);
        }
        return Optional.empty();
    }

    /** Where the band's bottom goes: a y in pane pixels, or {@code null} for «at the top, {@link #MARGIN} down». */
    private record Bottom(Integer y) {

        static Bottom ofPane(int paneHeight) {
            return new Bottom(paneHeight - MARGIN);
        }

        static Bottom at(int y) {
            return new Bottom(y);
        }

        static Bottom top() {
            return new Bottom(null);
        }
    }

    /** The band at most {@code width} wide, centred between {@code from} and {@code to}. */
    private static Placement band(String line, FontMetrics metrics, int width, int from, int to, Bottom bottom) {
        List<String> lines = wrap(line, metrics, width - 2 * PADDING_X);
        int textWidth = 0;
        for (String piece : lines) {
            textWidth = Math.max(textWidth, metrics.stringWidth(piece));
        }
        int bandWidth = Math.min(width, textWidth + 2 * PADDING_X);
        int bandHeight = lines.size() * metrics.getHeight() + (lines.size() - 1) * LINE_GAP + 2 * PADDING_Y;
        int x = from + (to - from - bandWidth) / 2;
        int y = bottom.y() == null ? MARGIN : bottom.y() - bandHeight;
        return new Placement(new Rectangle(x, y, bandWidth, bandHeight), lines);
    }

    private static boolean clear(Placement placement, Optional<Rectangle> obstacle) {
        return obstacle.map(panel -> !panel.intersects(placement.band())).orElse(true);
    }

    /**
     * {@code line} broken at spaces into at most {@link #MAXIMUM_LINES} lines at most {@code width} wide; the last
     * one ends in «…» when the line does not fit (as does a word wider than the band).
     */
    static List<String> wrap(String line, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        String current = "";
        for (String word : line.strip().split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (metrics.stringWidth(candidate) <= width) {
                current = candidate;
            } else if (current.isEmpty()) {
                lines.add(word); // wider than the band: cut below
            } else {
                lines.add(current);
                current = word;
            }
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        boolean cut = lines.size() > MAXIMUM_LINES;
        List<String> kept = new ArrayList<>(lines.subList(0, Math.min(MAXIMUM_LINES, lines.size())));
        for (int index = 0; index < kept.size(); index++) {
            kept.set(index, ellipsized(kept.get(index), metrics, width,
                    cut && index == kept.size() - 1));
        }
        return kept;
    }

    /** {@code text} within {@code width}, ending in «…» when it was cut or {@code more} follows. */
    private static String ellipsized(String text, FontMetrics metrics, int width, boolean more) {
        if (!more && metrics.stringWidth(text) <= width) {
            return text;
        }
        for (int length = text.length(); length > 0; length--) {
            String candidate = text.substring(0, length).stripTrailing() + ELLIPSIS;
            if (metrics.stringWidth(candidate) <= width) {
                return candidate;
            }
        }
        return ELLIPSIS;
    }

    // Painting

    /** Paints the subtitle in {@code pane}; {@code graphics} is in document coordinates, as overlays get it. */
    void paint(Graphics2D graphics, CircuitCanvas pane) {
        double opacity = alpha();
        if (text == null || opacity <= 0) {
            return;
        }
        Graphics2D screen = (Graphics2D) graphics.create();
        try {
            AffineTransform toScreen = pane.viewport().documentTransform().createInverse();
            screen.transform(toScreen); // back to the pane's logical pixels
            Optional<Placement> placement = placementIn(pane, screen);
            if (placement.isPresent()) {
                screen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                screen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                paint(screen, placement.get(), (float) opacity);
            }
        } catch (NoninvertibleTransformException exception) {
            // a degenerate view shows nothing
        } finally {
            screen.dispose();
        }
    }

    private static void paint(Graphics2D screen, Placement placement, float opacity) {
        Rectangle band = placement.band();
        RoundRectangle2D shape = new RoundRectangle2D.Double(band.x, band.y, band.width, band.height, ARC, ARC);
        screen.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity * BACKGROUND_OPACITY));
        screen.setColor(ReadableColors.panelBackground());
        screen.fill(shape);
        screen.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        screen.setColor(border());
        screen.setStroke(new BasicStroke(1f));
        screen.draw(shape);
        Font font = font();
        screen.setFont(font);
        screen.setColor(AiColors.foreground());
        FontMetrics metrics = screen.getFontMetrics(font);
        int y = band.y + PADDING_Y;
        for (String line : placement.lines()) {
            int x = band.x + (band.width - metrics.stringWidth(line)) / 2;
            screen.drawString(line, x, y + metrics.getAscent());
            y += metrics.getHeight() + LINE_GAP;
        }
    }

    private static Color border() {
        Color color = UIManager.getColor("Component.borderColor");
        return color != null ? color : Color.GRAY;
    }

    /** The theme's label font, a point larger: a subtitle reads from further away. */
    private static Font font() {
        Font base = UIManager.getFont("Label.font");
        Font font = base != null ? base : new Font(Font.DIALOG, Font.PLAIN, 12);
        return font.deriveFont(font.getSize2D() + 1f);
    }
}
