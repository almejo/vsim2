package cl.almejo.vsim.ui.panels;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/** Vector star, read from the current theme at paint time instead of depending on a font glyph. */
final class FavoriteStar implements Icon {

    private static final int SIZE = 18;
    private final boolean filled;

    FavoriteStar(boolean filled) {
        this.filled = filled;
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D drawing = (Graphics2D) graphics.create();
        try {
            drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color foreground = UIManager.getColor("Label.foreground");
            drawing.setColor(foreground == null ? component.getForeground() : foreground);
            drawing.setStroke(new BasicStroke(1.3f));
            Path2D star = new Path2D.Double();
            for (int point = 0; point < 10; point++) {
                double angle = -Math.PI / 2 + point * Math.PI / 5;
                double radius = point % 2 == 0 ? 7.5 : 3.3;
                double pointX = x + SIZE / 2.0 + Math.cos(angle) * radius;
                double pointY = y + SIZE / 2.0 + Math.sin(angle) * radius;
                if (point == 0) {
                    star.moveTo(pointX, pointY);
                } else {
                    star.lineTo(pointX, pointY);
                }
            }
            star.closePath();
            if (filled) {
                drawing.fill(star);
            } else {
                drawing.draw(star);
            }
        } finally {
            drawing.dispose();
        }
    }
}
