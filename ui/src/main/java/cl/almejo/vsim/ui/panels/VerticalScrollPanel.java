package cl.almejo.vsim.ui.panels;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * An inspector's panel («Propiedades», a machine's rows) in a scroll pane that only scrolls up and down: the rows take
 * the pane's width (at 150 % and 200 % they would otherwise stick out on the right, cut, since the pane never scrolls
 * sideways), and a panel taller than the inspector scrolls instead of being clipped.
 */
public class VerticalScrollPanel extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;
    private static final int UNIT_INCREMENT = 16;

    private final boolean shortPanelFillsPane;

    /**
     * A panel laid out by {@code layout}. When {@code shortPanelFillsPane}, a panel shorter than the pane stretches
     * to its height («Propiedades»); otherwise it keeps its own height (the machine's rows).
     */
    public VerticalScrollPanel(LayoutManager layout, boolean shortPanelFillsPane) {
        super(layout);
        this.shortPanelFillsPane = shortPanelFillsPane;
    }

    /**
     * This panel in a vertical scroll pane (never a horizontal one: the panel follows the pane's width). A text area
     * inside it that needs no scrolling of its own hands the mouse wheel to this pane (Swing forwards the wheel from
     * a scroll pane whose bar is hidden).
     */
    public JScrollPane inScrollPane() {
        JScrollPane scroll = new JScrollPane(this, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(UNIT_INCREMENT);
        return scroll;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return UNIT_INCREMENT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(UNIT_INCREMENT, visible.height - UNIT_INCREMENT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        // a short panel fills the pane when asked to; a tall one always keeps its height and scrolls
        return shortPanelFillsPane && getParent() instanceof JViewport viewport
                && getPreferredSize().height < viewport.getHeight();
    }
}
