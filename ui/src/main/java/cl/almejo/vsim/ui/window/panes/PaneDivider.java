package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The bar between two rows ({@link Orientation#HORIZONTAL}) or two columns ({@link Orientation#VERTICAL}) of panes,
 * drawn like FlatLaf's split pane dividers: its background and a few grip dots in the middle, with the theme's colours
 * read at paint time.
 */
public final class PaneDivider extends JComponent {

    private static final int DEFAULT_SIZE = 5;
    private static final int GRIP_DOTS = 3;
    private static final int GRIP_DOT_SIZE = 3;
    private static final int GRIP_GAP = 2;

    private final Orientation orientation;

    PaneDivider(Orientation orientation) {
        this.orientation = orientation;
        setOpaque(true);
        setCursor(Cursor.getPredefinedCursor(orientation == Orientation.HORIZONTAL
                ? Cursor.N_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR));
    }

    public Orientation orientation() {
        return orientation;
    }

    /** How thick a divider is, in pixels: the theme's split pane divider size. */
    static int thickness() {
        int size = UIManager.getInt("SplitPane.dividerSize");
        return size > 0 ? size : DEFAULT_SIZE;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setColor(color("SplitPane.background", getParent() != null ? getParent().getBackground()
                    : Color.LIGHT_GRAY));
            canvas.fillRect(0, 0, getWidth(), getHeight());
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setColor(color("SplitPaneDivider.gripColor", Color.GRAY));
            int length = GRIP_DOTS * GRIP_DOT_SIZE + (GRIP_DOTS - 1) * GRIP_GAP;
            boolean across = orientation == Orientation.HORIZONTAL;
            int start = ((across ? getWidth() : getHeight()) - length) / 2;
            int middle = ((across ? getHeight() : getWidth()) - GRIP_DOT_SIZE) / 2;
            for (int dot = 0; dot < GRIP_DOTS; dot++) {
                int along = start + dot * (GRIP_DOT_SIZE + GRIP_GAP);
                if (across) {
                    canvas.fillOval(along, middle, GRIP_DOT_SIZE, GRIP_DOT_SIZE);
                } else {
                    canvas.fillOval(middle, along, GRIP_DOT_SIZE, GRIP_DOT_SIZE);
                }
            }
        } finally {
            canvas.dispose();
        }
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
}
