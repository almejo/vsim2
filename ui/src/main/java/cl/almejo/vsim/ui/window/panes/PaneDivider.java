package cl.almejo.vsim.ui.window.panes;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.panes.CanvasPanes.Orientation;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;

/**
 * The bar between two rows ({@link Orientation#HORIZONTAL}) or two columns ({@link Orientation#VERTICAL}) of panes,
 * drawn like FlatLaf's split pane dividers: its background and a few grip dots in the middle, with the theme's colours
 * read at paint time. Dragging it, double-clicking it and its crossing with the other divider are handled by
 * {@link CanvasPanes}, which holds both dividers.
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
        setCursor(resizeCursor(orientation));
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public Orientation orientation() {
        return orientation;
    }

    /** How thick a divider is, in pixels: the theme's split pane divider size. */
    static int thickness() {
        int size = UIManager.getInt("SplitPane.dividerSize");
        return size > 0 ? size : DEFAULT_SIZE;
    }

    /** The resize cursor of a divider of {@code orientation}, the one the window's other dividers use. */
    static Cursor resizeCursor(Orientation orientation) {
        return Cursor.getPredefinedCursor(orientation == Orientation.HORIZONTAL
                ? Cursor.N_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR);
    }

    /** «Arrastra para cambiar el tamaño; doble clic para quitar la división», read when it is shown. */
    @Override
    public String getToolTipText() {
        return Messages.text("split.divider.tooltip");
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return getToolTipText();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            paintBar(canvas, orientation, new Rectangle(0, 0, getWidth(), getHeight()),
                    getParent() != null ? getParent().getBackground() : Color.LIGHT_GRAY);
        } finally {
            canvas.dispose();
        }
    }

    /**
     * Paints a divider bar of {@code orientation} filling {@code bar}: the theme's split pane background (else
     * {@code fallbackBackground}) and a few grip dots in its middle, colours read now. Also used for the preview of a
     * split pulled from a grip, so the preview looks exactly like the divider it becomes.
     */
    static void paintBar(Graphics2D graphics, Orientation orientation, Rectangle bar, Color fallbackBackground) {
        graphics.setColor(color("SplitPane.background", fallbackBackground));
        graphics.fill(bar);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(color("SplitPaneDivider.gripColor", Color.GRAY));
        int length = GRIP_DOTS * GRIP_DOT_SIZE + (GRIP_DOTS - 1) * GRIP_GAP;
        boolean across = orientation == Orientation.HORIZONTAL;
        int start = (across ? bar.x : bar.y) + ((across ? bar.width : bar.height) - length) / 2;
        int middle = (across ? bar.y : bar.x) + ((across ? bar.height : bar.width) - GRIP_DOT_SIZE) / 2;
        for (int dot = 0; dot < GRIP_DOTS; dot++) {
            int along = start + dot * (GRIP_DOT_SIZE + GRIP_GAP);
            if (across) {
                graphics.fillOval(along, middle, GRIP_DOT_SIZE, GRIP_DOT_SIZE);
            } else {
                graphics.fillOval(middle, along, GRIP_DOT_SIZE, GRIP_DOT_SIZE);
            }
        }
    }

    static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
}
