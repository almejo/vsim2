package cl.almejo.vsim.ui.whatsnew;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * The lit bulb beside «Consejo del día» (the user's idea, mockup v6): yellow in both themes, with a soft glow and
 * short rays; only its base takes the theme's muted colour, read at paint time. Drawn on the mockup's 24 × 24 grid.
 */
final class TipBulbIcon implements Icon {

    private static final int SIZE = 22;
    private static final double GRID = 24.0;
    /** The mockup's bulb: #ffd84a with a #b8860b outline, a 28 % glow and #e0a800 rays, the same in both themes. */
    private static final Color BULB = new Color(0xFFD84A);
    private static final Color GLOW = new Color(0xFF, 0xD8, 0x4A, 71);
    private static final Color OUTLINE = new Color(0xB8860B);
    private static final Color RAYS = new Color(0xE0A800);

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            painter.translate(x, y);
            double scale = getIconWidth() / GRID;
            painter.scale(scale, scale);
            painter.setColor(GLOW);
            painter.fill(new Ellipse2D.Double(2, 0, 20, 20));
            Path2D bulb = bulb();
            painter.setColor(BULB);
            painter.fill(bulb);
            painter.setStroke(new BasicStroke(1.2f));
            painter.setColor(OUTLINE);
            painter.draw(bulb);
            painter.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            painter.setColor(TipPicture.mutedColour());
            painter.draw(line(10, 19, 14, 19));
            painter.draw(line(10.5, 21, 13.5, 21));
            painter.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            painter.setColor(RAYS);
            painter.draw(line(12, 1, 12, 0.5));
            painter.draw(line(3, 10, 2, 10));
            painter.draw(line(22, 10, 21, 10));
            painter.draw(line(5, 3.5, 4.3, 2.8));
            painter.draw(line(19, 3.5, 19.7, 2.8));
        } finally {
            painter.dispose();
        }
    }

    /** The glass: a circle of radius 6 around (12, 9.5) narrowing into the neck at y 16.4 to 17. */
    private static Path2D bulb() {
        Path2D bulb = new Path2D.Double();
        bulb.moveTo(12, 3.5);
        bulb.curveTo(8.7, 3.5, 6, 6.2, 6, 9.5);
        bulb.curveTo(6, 11.4, 6.9, 13.1, 8.4, 14.3);
        bulb.curveTo(9.1, 14.9, 9.5, 15.6, 9.5, 16.4);
        bulb.lineTo(9.5, 17);
        bulb.lineTo(14.5, 17);
        bulb.lineTo(14.5, 16.4);
        bulb.curveTo(14.5, 15.6, 14.9, 14.9, 15.6, 14.3);
        bulb.curveTo(17.1, 13.1, 18, 11.4, 18, 9.5);
        bulb.curveTo(18, 6.2, 15.3, 3.5, 12, 3.5);
        bulb.closePath();
        return bulb;
    }

    private static Path2D line(double fromX, double fromY, double toX, double toY) {
        Path2D line = new Path2D.Double();
        line.moveTo(fromX, fromY);
        line.lineTo(toX, toY);
        return line;
    }

    @Override
    public int getIconWidth() {
        return UIScale.scale(SIZE);
    }

    @Override
    public int getIconHeight() {
        return UIScale.scale(SIZE);
    }
}
