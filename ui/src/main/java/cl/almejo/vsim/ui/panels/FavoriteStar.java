package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * The favourites star, drawn as a vector shape (no font glyph): an outline in the theme's disabled text colour for
 * «mark this», filled in a warm amber for a favourite, and in the selection's text colour on a selected row. The
 * colours are read from the current theme at paint time, never cached.
 */
final class FavoriteStar implements Icon {

    /** WCAG's minimum contrast for a graphical object (1.4.11): the filled star on the tree's background. */
    static final double MINIMUM_CONTRAST = 3;
    /** 3.5:1 on white; a brighter yellow would vanish on a light theme. */
    private static final Color LIGHT_FILLED = new Color(0xB77E00);
    /** Over 5:1 on FlatLaf Dark's tree. */
    private static final Color DARK_FILLED = new Color(0xFFC83D);
    private static final int SIZE = 18;
    private static final int POINTS = 10;
    private static final double OUTER_RADIUS = 7.5;
    private static final double INNER_RADIUS = 3.3;
    private static final float OUTLINE_WIDTH = 1.3f;

    private final boolean filled;
    private final boolean onSelection;

    FavoriteStar(boolean filled, boolean onSelection) {
        this.filled = filled;
        this.onSelection = onSelection;
    }

    /** The colour of a star {@code filled} or not, on a selected row or not, in the current theme. */
    static Color color(boolean filled, boolean onSelection) {
        if (!onSelection && filled) {
            return ReadableColors.dark() ? DARK_FILLED : LIGHT_FILLED;
        }
        Color themed = UIManager.getColor(onSelection ? "Tree.selectionForeground" : "Label.disabledForeground");
        return themed != null ? themed : Color.GRAY;
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
            drawing.setColor(color(filled, onSelection));
            drawing.setStroke(new BasicStroke(OUTLINE_WIDTH));
            Path2D star = shape(x, y);
            if (filled) {
                drawing.fill(star);
            } else {
                drawing.draw(star);
            }
        } finally {
            drawing.dispose();
        }
    }

    private static Path2D shape(int x, int y) {
        Path2D star = new Path2D.Double();
        for (int point = 0; point < POINTS; point++) {
            double angle = -Math.PI / 2 + point * Math.PI / 5;
            double radius = point % 2 == 0 ? OUTER_RADIUS : INNER_RADIUS;
            double pointX = x + SIZE / 2.0 + Math.cos(angle) * radius;
            double pointY = y + SIZE / 2.0 + Math.sin(angle) * radius;
            if (point == 0) {
                star.moveTo(pointX, pointY);
            } else {
                star.lineTo(pointX, pointY);
            }
        }
        star.closePath();
        return star;
    }
}
