package cl.almejo.vsim.ui.groups;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * A scroll pane of the «Grupos» window that shows its whole view up to {@code tallest} pixels and never gives up any
 * of that height to its neighbours: its least height is its preferred one. Only a view taller than {@code tallest}
 * scrolls up and down. A view wider than the pane scrolls sideways, and the pane then adds the horizontal scrollbar's
 * height so that the bar does not cover the bottom of the view (a plain scroll pane asks for the view's height alone
 * and the bar, when it shows, cuts the view).
 */
@SuppressWarnings("serial")
final class WholeHeightScrollPane extends JScrollPane {

    private final int tallest;
    /** Whether the last size asked for counted the horizontal scrollbar. */
    private boolean scrollbarCounted;

    WholeHeightScrollPane(Component view, int verticalPolicy, int tallest) {
        super(view, verticalPolicy, HORIZONTAL_SCROLLBAR_AS_NEEDED);
        this.tallest = tallest;
        setBorder(BorderFactory.createEmptyBorder());
        getVerticalScrollBar().setUnitIncrement(16);
        // a new width can make the view fit or stop fitting: ask for the height that goes with it
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (scrollsSideways() != scrollbarCounted) {
                    revalidate();
                }
            }
        });
    }

    private Component view() {
        return getViewport().getView();
    }

    /** The height of the view shown: all of it up to {@link #tallest}. */
    private int shownHeight() {
        Component view = view();
        int wanted = view instanceof Scrollable scrollable ? scrollable.getPreferredScrollableViewportSize().height
                : view.getPreferredSize().height;
        return Math.min(wanted, tallest);
    }

    private boolean scrollsUpAndDown() {
        return getVerticalScrollBarPolicy() != VERTICAL_SCROLLBAR_NEVER
                && view().getPreferredSize().height > shownHeight();
    }

    /** True when the view is wider than the room the pane has for it now (unknown before the first layout). */
    private boolean scrollsSideways() {
        if (getWidth() <= 0) {
            return false;
        }
        Insets insets = getInsets();
        int room = getWidth() - insets.left - insets.right
                - (scrollsUpAndDown() ? getVerticalScrollBar().getPreferredSize().width : 0);
        return view().getPreferredSize().width > room;
    }

    @Override
    public Dimension getPreferredSize() {
        Insets insets = getInsets();
        scrollbarCounted = scrollsSideways();
        int height = shownHeight() + insets.top + insets.bottom
                + (scrollbarCounted ? getHorizontalScrollBar().getPreferredSize().height : 0);
        return new Dimension(super.getPreferredSize().width, height);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(super.getMinimumSize().width, getPreferredSize().height);
    }
}
