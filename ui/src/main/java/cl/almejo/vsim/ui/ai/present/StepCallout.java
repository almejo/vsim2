package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.ai.AiColors;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
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
 * The callout of «Presentar» (spec §5): «Paso k de n» (plus «· propuesto» while the step waits), the step's text in
 * bold and its reason in grey, in a small box beside the change with a thin leader line and a dot pointing at it.
 * Worked out from each pane's view at paint time, so it follows pan and zoom, and painted only in the panes that show
 * the change (a step with nowhere to point shows in the active pane's corner). It fades in and out unless the user
 * asked for reduced motion. Colours from the scheme and the theme, read at paint time; editor chrome, never on paper.
 * EDT only.
 */
final class StepCallout {

    /** Where the box sits relative to the change. */
    enum Side { RIGHT, LEFT, BELOW, ABOVE, CORNER }

    /**
     * The box in pane pixels, the side it took, the dot on the change and where the leader line leaves the box.
     */
    record Placement(Rectangle box, Side side, Point2D dot, Point2D lineStart) {
    }

    /** What the callout says. */
    record Content(String header, String text, Optional<String> reason) {

        Content {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Pixels between the change and the box. */
    static final int GAP = 18;
    /** Pixels the box keeps from the pane's edges. */
    static final int PANE_PADDING = 8;
    static final int FADE_MILLISECONDS = 250;
    static final int TIMER_MILLISECONDS = 33;
    private static final long FADE_NANOS = TimeUnit.MILLISECONDS.toNanos(FADE_MILLISECONDS);
    private static final int INSIDE_PADDING = 8;
    private static final int ACCENT_WIDTH = 3;
    private static final int LINE_GAP = 3;
    private static final int MAXIMUM_TEXT_WIDTH = 260;
    private static final double DOT_RADIUS = 3.5;
    private static final BasicStroke LEADER = new BasicStroke(1.25f);
    private static final BasicStroke BORDER = new BasicStroke(1f);

    private final Supplier<ColorScheme> scheme;
    private final Supplier<CircuitCanvas> activePane;
    private final Function<CircuitCanvas, Optional<Rectangle>> covered;
    private final BooleanSupplier animate;
    private final Runnable repaint;
    private final LongSupplier nanoClock;
    private final Timer timer;
    private Content content;
    private Bounds target;
    private long shownAt;
    /** When the callout started fading out; {@code -1} while it is not leaving. */
    private long leavingSince = -1;
    private boolean disposed;

    /**
     * @param activePane where a callout with nowhere to point shows
     * @param animate    false when the user asked for reduced motion (read at each change)
     * @param repaint    repaints every pane
     */
    StepCallout(Supplier<ColorScheme> scheme, Supplier<CircuitCanvas> activePane, BooleanSupplier animate,
                Runnable repaint, LongSupplier nanoClock) {
        this(scheme, activePane, pane -> Optional.empty(), animate, repaint, nanoClock);
    }

    /** @param covered the part of a pane the floating panel covers (pane pixels): the box never goes under it */
    StepCallout(Supplier<ColorScheme> scheme, Supplier<CircuitCanvas> activePane,
                Function<CircuitCanvas, Optional<Rectangle>> covered, BooleanSupplier animate, Runnable repaint,
                LongSupplier nanoClock) {
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.activePane = Objects.requireNonNull(activePane, "activePane");
        this.covered = Objects.requireNonNull(covered, "covered");
        this.animate = Objects.requireNonNull(animate, "animate");
        this.repaint = Objects.requireNonNull(repaint, "repaint");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.timer = new Timer(TIMER_MILLISECONDS, event -> tick());
    }

    /** Shows {@code shown} pointing at {@code pointedAt} (grid units), replacing the callout there was. */
    void show(Content shown, Optional<Bounds> pointedAt) {
        if (disposed) {
            return;
        }
        boolean replacing = content != null && leavingSince < 0;
        content = Objects.requireNonNull(shown, "shown");
        target = pointedAt.orElse(null);
        leavingSince = -1;
        if (!replacing || !animate.getAsBoolean()) {
            // the next step's callout replaces a visible one in place; a new one fades in
            shownAt = animate.getAsBoolean() ? nanoClock.getAsLong() : Long.MIN_VALUE;
        }
        startFadingIfNeeded();
        repaint.run();
    }

    /** Takes the callout away (fading out unless reduced motion). */
    void clear() {
        if (content == null || leavingSince >= 0) {
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
        content = null;
        target = null;
        leavingSince = -1;
        timer.stop();
    }

    private void startFadingIfNeeded() {
        if (alpha() < 1.0 || leavingSince >= 0) {
            if (!timer.isRunning()) {
                timer.start();
            }
        }
    }

    /** One fade step: repaints, and drops the callout once it has faded out; stops the timer when nothing fades. */
    void tick() {
        long now = nanoClock.getAsLong();
        if (leavingSince >= 0 && now - leavingSince >= FADE_NANOS) {
            drop();
        } else if (leavingSince < 0 && alpha() >= 1.0) {
            timer.stop();
        }
        repaint.run();
    }

    /** The callout's opacity now, 0 to 1. */
    double alpha() {
        if (content == null) {
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

    /** What the callout says, while it shows (also while it fades out). */
    Optional<Content> content() {
        return Optional.ofNullable(content);
    }

    /** What it points at, in grid units. */
    Optional<Bounds> target() {
        return Optional.ofNullable(target);
    }

    void dispose() {
        disposed = true;
        drop();
    }

    // Colours, read from the theme at each paint: never cached.

    static Color background() {
        return ReadableColors.panelBackground();
    }

    static Color textColor() {
        return AiColors.foreground();
    }

    static Color mutedColor() {
        return AiColors.muted();
    }

    private static Color border() {
        Color color = UIManager.getColor("Component.borderColor");
        return color != null ? color : Color.GRAY;
    }

    private static Font baseFont() {
        Font font = UIManager.getFont("Label.font");
        return font != null ? font : new Font(Font.DIALOG, Font.PLAIN, 12);
    }

    // Placement

    /**
     * Where the callout goes in {@code pane}, measured with {@code measuring}'s fonts: empty when there is nothing to
     * show, the pane has no size, or the change is not in this pane's view (a step with nowhere to point shows in
     * the active pane only).
     */
    Optional<Placement> placementIn(CircuitCanvas pane, Graphics2D measuring) {
        if (content == null || pane.getWidth() <= 0 || pane.getHeight() <= 0) {
            return Optional.empty();
        }
        Dimension size = layout(measuring, content).size();
        Optional<Rectangle> obstacle = covered.apply(pane);
        if (target == null) {
            if (pane != activePane.get()) {
                return Optional.empty();
            }
            return Optional.of(corner(size, pane.getWidth(), pane.getHeight(), Optional.empty(), obstacle));
        }
        Rectangle2D onScreen = onScreen(pane, target);
        if (!onScreen.intersects(0, 0, pane.getWidth(), pane.getHeight())) {
            return Optional.empty();
        }
        return Optional.of(place(onScreen, size, pane.getWidth(), pane.getHeight(), obstacle));
    }

    private static Rectangle2D onScreen(CircuitCanvas pane, Bounds bounds) {
        Point2D corner = pane.viewport().gridToScreen(bounds.x(), bounds.y());
        Point2D end = pane.viewport().gridToScreen(bounds.maximumX(), bounds.maximumY());
        return new Rectangle2D.Double(corner.getX(), corner.getY(), end.getX() - corner.getX(),
                end.getY() - corner.getY());
    }

    /**
     * Beside {@code target} (pane pixels): on its right, else on its left (flipping near an edge), else below, else
     * above, else in the pane's top-right corner; always inside the pane, {@link #PANE_PADDING} from its edges.
     */
    static Placement place(Rectangle2D target, Dimension size, int paneWidth, int paneHeight) {
        return place(target, size, paneWidth, paneHeight, Optional.empty());
    }

    /**
     * As {@link #place(Rectangle2D, Dimension, int, int)}, never under {@code obstacle} (the floating panel, pane
     * pixels): a side whose box would fall under it is skipped (so the box flips away from the panel), and the
     * corner is the top-right one of the largest part of the pane the panel leaves free.
     */
    static Placement place(Rectangle2D target, Dimension size, int paneWidth, int paneHeight,
                           Optional<Rectangle> obstacle) {
        int width = size.width;
        int height = size.height;
        int besideY = clamp((int) Math.round(target.getCenterY() - height / 2.0), PANE_PADDING,
                paneHeight - PANE_PADDING - height);
        int aboveOrBelowX = clamp((int) Math.round(target.getCenterX() - width / 2.0), PANE_PADDING,
                paneWidth - PANE_PADDING - width);
        int rightX = (int) Math.ceil(target.getMaxX()) + GAP;
        Rectangle right = new Rectangle(rightX, besideY, width, height);
        if (rightX + width <= paneWidth - PANE_PADDING && clear(right, obstacle)) {
            return beside(right, Side.RIGHT, target, paneWidth, paneHeight);
        }
        int leftX = (int) Math.floor(target.getMinX()) - GAP - width;
        Rectangle left = new Rectangle(leftX, besideY, width, height);
        if (leftX >= PANE_PADDING && clear(left, obstacle)) {
            return beside(left, Side.LEFT, target, paneWidth, paneHeight);
        }
        int belowY = (int) Math.ceil(target.getMaxY()) + GAP;
        Rectangle below = new Rectangle(aboveOrBelowX, belowY, width, height);
        if (belowY + height <= paneHeight - PANE_PADDING && clear(below, obstacle)) {
            return beside(below, Side.BELOW, target, paneWidth, paneHeight);
        }
        int aboveY = (int) Math.floor(target.getMinY()) - GAP - height;
        Rectangle above = new Rectangle(aboveOrBelowX, aboveY, width, height);
        if (aboveY >= PANE_PADDING && clear(above, obstacle)) {
            return beside(above, Side.ABOVE, target, paneWidth, paneHeight);
        }
        return corner(size, paneWidth, paneHeight, Optional.of(target), obstacle);
    }

    private static Placement beside(Rectangle box, Side side, Rectangle2D target, int paneWidth, int paneHeight) {
        double dotX = switch (side) {
            case RIGHT -> target.getMaxX();
            case LEFT -> target.getMinX();
            default -> clamp(box.getCenterX(), target.getMinX(), target.getMaxX());
        };
        double dotY = switch (side) {
            case BELOW -> target.getMaxY();
            case ABOVE -> target.getMinY();
            default -> clamp(box.getCenterY(), target.getMinY(), target.getMaxY());
        };
        Point2D dot = new Point2D.Double(clamp(dotX, 0, paneWidth), clamp(dotY, 0, paneHeight));
        Point2D lineStart = switch (side) {
            case RIGHT -> new Point2D.Double(box.getMinX(), clamp(dot.getY(), box.getMinY(), box.getMaxY()));
            case LEFT -> new Point2D.Double(box.getMaxX(), clamp(dot.getY(), box.getMinY(), box.getMaxY()));
            case BELOW -> new Point2D.Double(clamp(dot.getX(), box.getMinX(), box.getMaxX()), box.getMinY());
            default -> new Point2D.Double(clamp(dot.getX(), box.getMinX(), box.getMaxX()), box.getMaxY());
        };
        return new Placement(box, side, dot, lineStart);
    }

    private static boolean clear(Rectangle box, Optional<Rectangle> obstacle) {
        return obstacle.map(panel -> !panel.intersects(box)).orElse(true);
    }

    /**
     * In the top-right corner of the pane (or of its largest part the floating panel leaves free), the dot on the
     * visible middle of {@code target} when there is one.
     */
    private static Placement corner(Dimension size, int paneWidth, int paneHeight, Optional<Rectangle2D> target,
                                    Optional<Rectangle> obstacle) {
        Rectangle free = obstacle.map(panel -> freePart(panel, paneWidth, paneHeight))
                .orElse(new Rectangle(0, 0, paneWidth, paneHeight));
        Rectangle box = new Rectangle(Math.max(free.x + PANE_PADDING, free.x + free.width - PANE_PADDING - size.width),
                free.y + PANE_PADDING, size.width, size.height);
        Optional<Rectangle2D> visible = target.map(area -> area.createIntersection(
                new Rectangle2D.Double(0, 0, paneWidth, paneHeight))).filter(area -> !area.isEmpty());
        Point2D dot = visible.<Point2D>map(area -> new Point2D.Double(area.getCenterX(), area.getCenterY()))
                .orElse(new Point2D.Double(box.getMinX(), box.getMinY()));
        Point2D lineStart = new Point2D.Double(clamp(dot.getX(), box.getMinX(), box.getMaxX()),
                clamp(dot.getY(), box.getMinY(), box.getMaxY()));
        return new Placement(box, Side.CORNER, visible.isPresent() ? dot : lineStart, lineStart);
    }

    /** The largest part of the pane left, right, above or below {@code panel}. */
    static Rectangle freePart(Rectangle panel, int paneWidth, int paneHeight) {
        Rectangle under = panel.intersection(new Rectangle(0, 0, paneWidth, paneHeight));
        if (under.isEmpty()) {
            return new Rectangle(0, 0, paneWidth, paneHeight);
        }
        List<Rectangle> candidates = List.of(
                new Rectangle(0, 0, under.x, paneHeight),
                new Rectangle(under.x + under.width, 0, paneWidth - under.x - under.width, paneHeight),
                new Rectangle(0, 0, paneWidth, under.y),
                new Rectangle(0, under.y + under.height, paneWidth, paneHeight - under.y - under.height));
        Rectangle best = candidates.getFirst();
        for (Rectangle candidate : candidates) {
            if ((long) candidate.width * candidate.height > (long) best.width * best.height) {
                best = candidate;
            }
        }
        return best.isEmpty() ? new Rectangle(0, 0, paneWidth, paneHeight) : best;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    // Painting

    /** Paints the callout in {@code pane}; {@code graphics} is in document coordinates, as overlays get it. */
    void paint(Graphics2D graphics, CircuitCanvas pane) {
        double opacity = alpha();
        if (content == null || opacity <= 0) {
            return;
        }
        Graphics2D screen = (Graphics2D) graphics.create();
        try {
            AffineTransform toScreen = pane.viewport().documentTransform().createInverse();
            screen.transform(toScreen); // back to the pane's logical pixels
            Optional<Placement> placement = placementIn(pane, screen);
            if (placement.isPresent()) {
                screen.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
                screen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                screen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                paint(screen, placement.get(), layout(screen, content));
            }
        } catch (NoninvertibleTransformException exception) {
            // a degenerate view shows nothing
        } finally {
            screen.dispose();
        }
    }

    private void paint(Graphics2D screen, Placement placement, Layout layout) {
        Color accent = scheme.get().selection();
        Rectangle box = placement.box();
        boolean pointing = !placement.dot().equals(placement.lineStart());
        if (pointing) {
            screen.setColor(accent);
            screen.setStroke(LEADER);
            screen.draw(new Line2D.Double(placement.lineStart(), placement.dot()));
            screen.fill(new Ellipse2D.Double(placement.dot().getX() - DOT_RADIUS,
                    placement.dot().getY() - DOT_RADIUS, 2 * DOT_RADIUS, 2 * DOT_RADIUS));
        }
        RoundRectangle2D shape = new RoundRectangle2D.Double(box.x, box.y, box.width, box.height, 8, 8);
        screen.setColor(background());
        screen.fill(shape);
        screen.setColor(border());
        screen.setStroke(BORDER);
        screen.draw(shape);
        screen.setColor(accent);
        screen.fill(new Rectangle2D.Double(box.x + 1, box.y + INSIDE_PADDING / 2.0, ACCENT_WIDTH,
                box.height - INSIDE_PADDING));
        int x = box.x + INSIDE_PADDING + ACCENT_WIDTH;
        int y = box.y + INSIDE_PADDING;
        for (Line line : layout.lines()) {
            screen.setFont(line.font());
            screen.setColor(line.muted() ? mutedColor() : textColor());
            FontMetrics metrics = screen.getFontMetrics();
            screen.drawString(line.text(), x, y + metrics.getAscent());
            y += metrics.getHeight() + LINE_GAP;
        }
    }

    private record Line(String text, Font font, boolean muted) {
    }

    private record Layout(List<Line> lines, Dimension size) {
    }

    /** The header, the text wrapped in bold and the reason wrapped in grey, and the box they need. */
    private static Layout layout(Graphics2D measuring, Content shown) {
        Font base = baseFont();
        Font header = base.deriveFont(base.getSize2D() - 1f);
        Font bold = base.deriveFont(Font.BOLD);
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(shown.header(), header, true));
        for (String piece : wrap(shown.text(), measuring.getFontMetrics(bold))) {
            lines.add(new Line(piece, bold, false));
        }
        shown.reason().filter(reason -> !reason.isBlank()).ifPresent(reason -> {
            for (String piece : wrap(reason, measuring.getFontMetrics(base))) {
                lines.add(new Line(piece, base, true));
            }
        });
        int width = 0;
        int height = 0;
        for (Line line : lines) {
            FontMetrics metrics = measuring.getFontMetrics(line.font());
            width = Math.max(width, metrics.stringWidth(line.text()));
            height += metrics.getHeight();
        }
        height += LINE_GAP * (lines.size() - 1);
        return new Layout(lines, new Dimension(width + 2 * INSIDE_PADDING + ACCENT_WIDTH,
                height + 2 * INSIDE_PADDING));
    }

    /** {@code text} broken at spaces into lines at most {@link #MAXIMUM_TEXT_WIDTH} wide (a longer word stays whole). */
    private static List<String> wrap(String text, FontMetrics metrics) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && metrics.stringWidth(candidate) > MAXIMUM_TEXT_WIDTH) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }
}
