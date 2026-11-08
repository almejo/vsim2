package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.FrameTicker;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * A small rounded dark label shown for a while over the canvas area of a view, at the top or bottom centre
 * («Ventana copiada al portapapeles», «Modo presentación · Esc o F11 para salir…»). It lives in the window's layered
 * pane so nothing under it moves, fades in and out unless animations are off, and takes itself away. At most one is
 * shown over an area: a new one replaces the one still there.
 */
public final class CanvasNotice extends JComponent {

    /** Where over the area the notice sits, horizontally centred. */
    public enum Position { TOP, BOTTOM }

    private static final int MARGIN = 16;
    private static final int PADDING_X = 16;
    private static final int PADDING_Y = 8;
    private static final int CORNER = 14;
    private static final int GAP = 6;
    private static final AtomicInteger STACKED_COUNTER = new AtomicInteger();
    private static final long FADE_MILLISECONDS = 180;
    private static final int BACKGROUND_ALPHA = 224;
    private static final String SHOWN_PROPERTY = "vsim.canvasNotice";

    private final String text;
    private final JComponent over;
    private final Position position;
    private float opacity;
    private int level;
    private String slot = SHOWN_PROPERTY;
    private Runnable gone;
    private JLayeredPane layers;
    private Runnable hider = () -> {
    };

    private CanvasNotice(JComponent over, String text, Position position) {
        this.over = over;
        this.text = text;
        this.position = position;
        setOpaque(false);
        setFont(over.getFont());
        FontMetrics metrics = getFontMetrics(getFont());
        setSize(metrics.stringWidth(text) + 2 * PADDING_X, metrics.getHeight() + 2 * PADDING_Y);
    }

    /** Like the full overload, with the system clock and fading as {@link Animations} says for {@code preferences}. */
    public static Runnable show(JComponent over, String text, Position position, Duration duration,
                                FrameTicker ticker, AppPreferences preferences) {
        return show(over, text, position, duration, ticker, System::currentTimeMillis,
                Animations.enabled(preferences));
    }

    /** Like the full overload, with the system clock and fading on. */
    public static Runnable show(JComponent over, String text, Position position, Duration duration,
                                FrameTicker ticker) {
        return show(over, text, position, duration, ticker, System::currentTimeMillis, true);
    }

    /**
     * Shows {@code text} over {@code over} for {@code duration}, driven by {@code ticker} (one frame repositions and
     * fades it; the frame past the duration removes it and stops the ticker).
     *
     * @param nowMillis the clock the frames read (a fake one in tests)
     * @param fade      false: it appears and disappears at once
     * @return what removes it before its time; harmless when it is already gone
     */
    public static Runnable show(JComponent over, String text, Position position, Duration duration,
                                FrameTicker ticker, LongSupplier nowMillis, boolean fade) {
        CanvasNotice notice = create(over, text, position, duration, ticker, nowMillis, fade, SHOWN_PROPERTY, null);
        return notice == null ? () -> {
        } : notice::takeAway;
    }

    /**
     * Like {@link #show(JComponent, String, Position, Duration, FrameTicker, LongSupplier, boolean)}, but one of
     * several notices over the same area ({@link CanvasNoticeStack}): it replaces nothing, sits {@code level}
     * notices away from the edge (see {@link #setLevel}) and tells {@code gone} when it is taken away.
     *
     * @return the notice, or null when {@code over} is not in a window
     */
    static CanvasNotice showStacked(JComponent over, String text, Position position, Duration duration,
                                    FrameTicker ticker, LongSupplier nowMillis, boolean fade, Runnable gone) {
        return create(over, text, position, duration, ticker, nowMillis, fade,
                SHOWN_PROPERTY + "#" + STACKED_COUNTER.incrementAndGet(), gone);
    }

    private static CanvasNotice create(JComponent over, String text, Position position, Duration duration,
                                       FrameTicker ticker, LongSupplier nowMillis, boolean fade, String slot,
                                       Runnable gone) {
        Objects.requireNonNull(over, "over");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(position, "position");
        JRootPane rootPane = SwingUtilities.getRootPane(over);
        if (rootPane == null) {
            return null;
        }
        JLayeredPane layers = rootPane.getLayeredPane();
        replaceShownOver(over, slot);
        CanvasNotice notice = new CanvasNotice(over, text, position);
        notice.opacity = fade ? 0f : 1f;
        notice.slot = slot;
        notice.gone = gone;
        notice.layers = layers;
        over.putClientProperty(slot, notice);
        layers.add(notice, JLayeredPane.POPUP_LAYER);
        long start = nowMillis.getAsLong();
        notice.hider = () -> notice.remove(layers, over, ticker);
        Runnable frame = () -> {
            long elapsed = nowMillis.getAsLong() - start;
            if (elapsed >= duration.toMillis()) {
                notice.takeAway();
                return;
            }
            notice.opacity = fade ? fadeAt(elapsed, duration.toMillis()) : 1f;
            notice.reposition(layers);
        };
        notice.reposition(layers);
        ticker.start(frame);
        return notice;
    }

    /** Takes the notice away now (and stops its ticker); harmless when it is already gone. */
    void takeAway() {
        hider.run();
    }

    /** How many notices of the stack sit between this one and the edge (0: next to it). */
    void setLevel(int level) {
        this.level = level;
        if (layers != null) {
            reposition(layers);
        }
    }

    /** The text shown. */
    public String text() {
        return text;
    }

    /** A notice still in the slot goes, with its ticker (it would keep running for nothing otherwise). */
    private static void replaceShownOver(JComponent over, String slot) {
        if (over.getClientProperty(slot) instanceof CanvasNotice previous) {
            previous.takeAway();
        }
        over.putClientProperty(slot, null);
    }

    private static float fadeAt(long elapsed, long total) {
        long remaining = total - elapsed;
        long ramp = Math.min(elapsed, remaining);
        return Math.min(1f, Math.max(0f, (float) ramp / FADE_MILLISECONDS));
    }

    private void remove(JLayeredPane layers, JComponent area, FrameTicker ticker) {
        if (area.getClientProperty(slot) == this) {
            area.putClientProperty(slot, null);
            ticker.stop();
        }
        if (getParent() != null) {
            Rectangle bounds = getBounds();
            layers.remove(this);
            layers.repaint(bounds);
        }
        Runnable told = gone;
        gone = null;
        if (told != null) {
            told.run();
        }
    }

    private void reposition(JLayeredPane layers) {
        Rectangle area = new Rectangle(SwingUtilities.convertPoint(over, 0, 0, layers), over.getSize());
        int horizontal = area.x + (area.width - getWidth()) / 2;
        int offset = level * (getHeight() + GAP);
        int vertical = position == Position.TOP ? area.y + MARGIN + offset
                : area.y + area.height - getHeight() - MARGIN - offset;
        setLocation(horizontal, vertical);
        repaint();
    }

    /** The tooltip colours of the current theme (dark in light themes, as a toast), read at paint time, translucent. */
    static Color toastBackground() {
        Color base = UIManager.getColor("ToolTip.background");
        if (base == null) {
            base = Color.DARK_GRAY;
        }
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), BACKGROUND_ALPHA);
    }

    static Color toastForeground() {
        Color foreground = UIManager.getColor("ToolTip.foreground");
        return foreground == null ? Color.WHITE : foreground;
    }

    @Override
    public Dimension getPreferredSize() {
        return getSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            painter.setComposite(AlphaComposite.SrcOver.derive(opacity));
            painter.setColor(toastBackground());
            painter.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER, CORNER);
            painter.setColor(toastForeground());
            painter.setFont(getFont());
            painter.drawString(text, PADDING_X, PADDING_Y + painter.getFontMetrics().getAscent());
        } finally {
            painter.dispose();
        }
    }
}
