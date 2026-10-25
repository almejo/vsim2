package cl.almejo.vsim.ui.appearance;

import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.EnumSet;
import java.util.Set;

/**
 * The one-pixel separator lines between the shape editor's bands (scope line, toolbar, canvas, side panel, footer),
 * in the theme's {@code Component.borderColor} read at each paint, plus an inner padding.
 */
@SuppressWarnings("serial")
final class EdgeBorder extends AbstractBorder {
    /** Which sides get a line. */
    enum Side { TOP, LEFT, BOTTOM, RIGHT }

    private final Set<Side> sides;
    private final Insets padding;

    EdgeBorder(Set<Side> sides, Insets padding) {
        this.sides = EnumSet.noneOf(Side.class);
        this.sides.addAll(sides);
        this.padding = (Insets) padding.clone();
    }

    @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
        Color line = UIManager.getColor("Component.borderColor");
        graphics.setColor(line != null ? line : Color.GRAY);
        if (sides.contains(Side.TOP)) { graphics.fillRect(x, y, width, 1); }
        if (sides.contains(Side.BOTTOM)) { graphics.fillRect(x, y + height - 1, width, 1); }
        if (sides.contains(Side.LEFT)) { graphics.fillRect(x, y, 1, height); }
        if (sides.contains(Side.RIGHT)) { graphics.fillRect(x + width - 1, y, 1, height); }
    }

    @Override public Insets getBorderInsets(Component component, Insets insets) {
        insets.top = padding.top + (sides.contains(Side.TOP) ? 1 : 0);
        insets.left = padding.left + (sides.contains(Side.LEFT) ? 1 : 0);
        insets.bottom = padding.bottom + (sides.contains(Side.BOTTOM) ? 1 : 0);
        insets.right = padding.right + (sides.contains(Side.RIGHT) ? 1 : 0);
        return insets;
    }
}
