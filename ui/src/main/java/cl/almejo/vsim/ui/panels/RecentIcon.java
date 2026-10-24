package cl.almejo.vsim.ui.panels;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * The clock of the «Recientes» heading, drawn as a vector shape in the theme's text colour (the selection's text
 * colour on a selected row), read at paint time, never cached.
 */
final class RecentIcon implements Icon {

    private static final int SIZE = 18;
    private static final double RADIUS = 6.5;
    private static final double HOUR_HAND = 3.2;
    private static final double MINUTE_HAND = 4.5;
    private static final float STROKE_WIDTH = 1.4f;

    private final boolean onSelection;

    RecentIcon(boolean onSelection) {
        this.onSelection = onSelection;
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
            Color themed = UIManager.getColor(onSelection ? "Tree.selectionForeground" : "Label.foreground");
            drawing.setColor(themed != null ? themed : component.getForeground());
            drawing.setStroke(new BasicStroke(STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double centerX = x + SIZE / 2.0;
            double centerY = y + SIZE / 2.0;
            drawing.draw(new Ellipse2D.Double(centerX - RADIUS, centerY - RADIUS, 2 * RADIUS, 2 * RADIUS));
            Path2D hands = new Path2D.Double();
            hands.moveTo(centerX, centerY - MINUTE_HAND);
            hands.lineTo(centerX, centerY);
            hands.lineTo(centerX + HOUR_HAND, centerY);
            drawing.draw(hands);
        } finally {
            drawing.dispose();
        }
    }
}
