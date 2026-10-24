package cl.almejo.vsim.ui.appearance;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** Small vector tool glyphs using the current Swing foreground, with no cached theme colors. */
final class ShapeToolIcon implements Icon {
    private final String name;

    ShapeToolIcon(String name) { this.name = name; }
    @Override public int getIconWidth() { return 24; }
    @Override public int getIconHeight() { return 24; }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D glyph = (Graphics2D) graphics.create();
        try {
            glyph.translate(x, y);
            glyph.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            glyph.setColor(component.isEnabled() ? component.getForeground() : UIManager.getColor("Label.disabledForeground"));
            glyph.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (name) {
                case "rectangle" -> glyph.draw(new Rectangle2D.Double(4, 6, 16, 12));
                case "ellipse" -> glyph.draw(new Ellipse2D.Double(3, 5, 18, 14));
                case "line" -> glyph.draw(new Line2D.Double(4, 19, 20, 5));
                case "triangle", "polygon", "nodes" -> {
                    Path2D path = new Path2D.Double();
                    path.moveTo(4, 4); path.lineTo(20, 12); path.lineTo(4, 20); path.closePath(); glyph.draw(path);
                    if (!name.equals("triangle")) {
                        glyph.fillRect(2, 2, 4, 4); glyph.fillRect(18, 10, 4, 4); glyph.fillRect(2, 18, 4, 4);
                    }
                }
                case "text" -> {
                    glyph.drawLine(5, 5, 19, 5); glyph.drawLine(12, 5, 12, 20); glyph.drawLine(8, 20, 16, 20);
                }
                case "curve" -> {
                    Path2D path = new Path2D.Double(); path.moveTo(4, 20); path.quadTo(5, 2, 20, 4); glyph.draw(path);
                    glyph.draw(new Rectangle2D.Double(2, 18, 4, 4)); glyph.draw(new Rectangle2D.Double(18, 2, 4, 4));
                }
                case "nodeAdd", "nodeRemove" -> {
                    glyph.drawLine(3, 16, 21, 7); glyph.fillRect(10, 10, 5, 5);
                    glyph.drawLine(15, 19, 23, 19);
                    if (name.equals("nodeAdd")) { glyph.drawLine(19, 15, 19, 23); }
                }
                case "front", "back" -> {
                    glyph.drawRect(3, 9, 12, 12);
                    if (name.equals("front")) { glyph.fillRect(9, 3, 12, 12); }
                    else { glyph.drawRect(9, 3, 12, 12); glyph.fillRect(3, 9, 12, 12); }
                }
                case "fill" -> { glyph.drawRect(4, 4, 16, 16); glyph.fillRect(5, 12, 15, 8); }
                case "shapes" -> {
                    glyph.drawRect(2, 3, 10, 10); glyph.drawOval(9, 9, 11, 11);
                    glyph.drawLine(18, 4, 22, 4); glyph.drawLine(20, 6, 22, 4);
                }
                default -> { glyph.drawRect(5, 5, 14, 14); glyph.drawLine(5, 5, 19, 19); }
            }
        } finally { glyph.dispose(); }
    }
}
