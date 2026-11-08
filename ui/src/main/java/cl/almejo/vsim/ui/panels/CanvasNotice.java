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
    private static final long FADE_MILLISECONDS = 180;
    private static final int BACKGROUND_ALPHA = 224;
    private static final String SHOWN_PROPERTY = "vsim.canvasNotice";

    private final String text;
    private final JComponent over;
    private final Position position;
    private float opacity;

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
        Objects.requireNonNull(over, "over");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(position, "position");
        JRootPane rootPane = SwingUtilities.getRootPane(over);
        if (rootPane == null) {
            return () -> {
            };
        }
        JLayeredPane layers = rootPane.getLayeredPane();
        replaceShownOver(over);
        CanvasNotice notice = new CanvasNotice(over, text, position);
        notice.opacity = fade ? 0f : 1f;
        over.putClientProperty(SHOWN_PROPERTY, notice);
        layers.add(notice, JLayeredPane.POPUP_LAYER);
        long start = nowMillis.getAsLong();
        Runnable hide = () -> notice.remove(layers, over, ticker);
        Runnable frame = () -> {
            long elapsed = nowMillis.getAsLong() - start;
            if (elapsed >= duration.toMillis()) {
                hide.run();
                return;
            }
            notice.opacity = fade ? fadeAt(elapsed, duration.toMillis()) : 1f;
            notice.reposition(layers);
        };
        notice.reposition(layers);
        ticker.start(frame);
        return hide;
    }

    /** The text shown. */
    public String text() {
        return text;
    }

    private static void replaceShownOver(JComponent over) {
        if (over.getClientProperty(SHOWN_PROPERTY) instanceof CanvasNotice previous && previous.getParent() != null) {
            previous.getParent().remove(previous);
        }
        over.putClientProperty(SHOWN_PROPERTY, null);
    }

    private static float fadeAt(long elapsed, long total) {
        long remaining = total - elapsed;
        long ramp = Math.min(elapsed, remaining);
        return Math.min(1f, Math.max(0f, (float) ramp / FADE_MILLISECONDS));
    }

    private void remove(JLayeredPane layers, JComponent area, FrameTicker ticker) {
        if (area.getClientProperty(SHOWN_PROPERTY) == this) {
            area.putClientProperty(SHOWN_PROPERTY, null);
            ticker.stop();
        }
        if (getParent() != null) {
            Rectangle bounds = getBounds();
            layers.remove(this);
            layers.repaint(bounds);
        }
    }

    private void reposition(JLayeredPane layers) {
        Rectangle area = new Rectangle(SwingUtilities.convertPoint(over, 0, 0, layers), over.getSize());
        int horizontal = area.x + (area.width - getWidth()) / 2;
        int vertical = position == Position.TOP ? area.y + MARGIN : area.y + area.height - getHeight() - MARGIN;
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
