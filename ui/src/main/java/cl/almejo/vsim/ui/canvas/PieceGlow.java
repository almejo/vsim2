package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;

/**
 * The glow «Ver la pieza» gives the piece it travels to (spec §5.1): a halo around it that fades out over
 * {@link #DURATION_MILLIS}. Time is given by the caller (the canvas's clock), the colour is read from the scheme at
 * every paint, never kept.
 */
final class PieceGlow {

    /** How long the glow lasts. */
    static final long DURATION_MILLIS = 1500;
    /** Grid units the halo's outer ring reaches beyond the piece. */
    private static final double REACH = 1.5;
    private static final int RINGS = 3;

    private Optional<Bounds> piece = Optional.empty();
    private long startMillis;

    /** Starts (or restarts) the glow around {@code bounds} at {@code nowMillis}. */
    void start(Bounds bounds, long nowMillis) {
        piece = Optional.of(bounds);
        startMillis = nowMillis;
    }

    void stop() {
        piece = Optional.empty();
    }

    boolean activeAt(long nowMillis) {
        return piece.isPresent() && nowMillis - startMillis < DURATION_MILLIS;
    }

    /** From 1 (just started) down to 0 (over, or none); linear, so the fade reads as even. */
    double strengthAt(long nowMillis) {
        if (!activeAt(nowMillis)) {
            return 0;
        }
        return 1.0 - (double) Math.max(0, nowMillis - startMillis) / DURATION_MILLIS;
    }

    /** Paints the halo in document coordinates; nothing when it is over. {@code steady}: no fade (reduced motion). */
    void paint(Graphics2D graphics, ColorScheme scheme, long nowMillis, boolean steady) {
        if (!activeAt(nowMillis)) {
            return;
        }
        Bounds bounds = piece.orElseThrow();
        double strength = steady ? 1.0 : strengthAt(nowMillis);
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = scheme.selection();
            double unit = GridPoint.PIXELS_PER_UNIT;
            for (int ring = RINGS; ring >= 1; ring--) {
                double margin = REACH * ring / RINGS * unit;
                float alpha = (float) (0.45 * strength / ring);
                local.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                local.setColor(color);
                local.setStroke(new BasicStroke((float) (unit * 0.9)));
                local.draw(new RoundRectangle2D.Double(bounds.x() * unit - margin, bounds.y() * unit - margin,
                        bounds.width() * unit + 2 * margin, bounds.height() * unit + 2 * margin,
                        unit * 1.5, unit * 1.5));
            }
            local.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) strength));
            local.setStroke(new BasicStroke(2f));
            local.draw(new RoundRectangle2D.Double(bounds.x() * unit - 2, bounds.y() * unit - 2,
                    bounds.width() * unit + 4, bounds.height() * unit + 4, unit, unit));
        } finally {
            local.dispose();
        }
    }
}
