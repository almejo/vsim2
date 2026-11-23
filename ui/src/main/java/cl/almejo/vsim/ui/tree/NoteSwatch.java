package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** The small square a note's row of the circuit tree shows, in that note's paper colour (read when painted). */
final class NoteSwatch implements Icon {

    private static final int SIZE = 16;
    private static final int MARGIN = 3;
    private static final float OUTLINE_WIDTH = 1f;

    private final StickyColor color;
    private final Appearance appearance;

    NoteSwatch(StickyColor color, Appearance appearance) {
        this.color = color;
        this.appearance = appearance;
    }

    /** The paper colour of the note in {@code scheme}. */
    Color paper(ColorScheme scheme) {
        return scheme.stickyPaper(color);
    }

    @Override
    public void paintIcon(Component owner, Graphics graphics, int x, int y) {
        ColorScheme scheme = appearance.scheme();
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int margin = UIScale.scale(MARGIN);
            int side = getIconWidth() - 2 * margin;
            int arc = UIScale.scale(3);
            painter.setColor(paper(scheme));
            painter.fillRoundRect(x + margin, y + margin, side, side, arc, arc);
            painter.setColor(scheme.stickyFold(color));
            painter.setStroke(new BasicStroke(UIScale.scale(OUTLINE_WIDTH)));
            painter.drawRoundRect(x + margin, y + margin, side, side, arc, arc);
        } finally {
            painter.dispose();
        }
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
