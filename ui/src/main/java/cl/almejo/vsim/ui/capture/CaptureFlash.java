package cl.almejo.vsim.ui.capture;

import cl.almejo.vsim.ui.canvas.FrameTicker;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * The short white flash over what a capture copied: a translucent white veil in the window's layered pane, over the
 * copied area, that fades out (with animations off it stays whole and goes at once) and takes itself away. Never part of a capture (the copy is painted before it is
 * shown). Mouse events go through it.
 */
final class CaptureFlash extends JComponent {

    static final Duration DURATION = Duration.ofMillis(350);
    private static final float PEAK_OPACITY = 0.75f;

    private float opacity = PEAK_OPACITY;

    private CaptureFlash() {
        setOpaque(false);
    }

    /**
     * Flashes {@code area} for {@link #DURATION}, one frame at a time from {@code ticker}.
     *
     * @return what takes the flash away before its time; harmless when it is already gone; a no-op when
     * {@code area} is in no window
     */
    static Runnable show(JComponent area, FrameTicker ticker, LongSupplier nowMillis,
                         BooleanSupplier animations) {
        JRootPane rootPane = SwingUtilities.getRootPane(area);
        if (rootPane == null) {
            return () -> {
            };
        }
        JLayeredPane layers = rootPane.getLayeredPane();
        CaptureFlash flash = new CaptureFlash();
        layers.add(flash, JLayeredPane.POPUP_LAYER);
        flash.setBounds(new Rectangle(SwingUtilities.convertPoint(area, 0, 0, layers), area.getSize()));
        flash.repaint();
        long start = nowMillis.getAsLong();
        Runnable hide = () -> {
            ticker.stop();
            if (flash.getParent() != null) {
                Rectangle bounds = flash.getBounds();
                layers.remove(flash);
                layers.repaint(bounds);
            }
        };
        ticker.start(() -> {
            long elapsed = nowMillis.getAsLong() - start;
            if (elapsed >= DURATION.toMillis()) {
                hide.run();
                return;
            }
            // with «Animaciones: No» it stays at its peak, whole, until its time is up: no fade
            flash.opacity = animations.getAsBoolean()
                    ? PEAK_OPACITY * (1f - (float) elapsed / DURATION.toMillis()) : PEAK_OPACITY;
            flash.setBounds(new Rectangle(SwingUtilities.convertPoint(area, 0, 0, layers), area.getSize()));
            flash.repaint();
        });
        return hide;
    }

    float opacity() {
        return opacity;
    }

    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, opacity))));
            painter.setColor(Color.WHITE);
            painter.fillRect(0, 0, getWidth(), getHeight());
        } finally {
            painter.dispose();
        }
    }
}
