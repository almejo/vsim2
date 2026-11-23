package cl.almejo.vsim.ui.window;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Lays a toolbar out in one row, always (audit V1: the bar never wraps), from the left, with no glue and no right
 * group. When everything does not fit, the components that do not fit leave the bar from the right end, keeping the
 * order, and the {@code overflowButton} (a «»» right after what stays) shows; it opens a popup with them
 * ({@link ToolbarOverflowMenu}). A hidden component keeps being visible and a child of the bar, with empty bounds, so
 * the lists that read the toolbar (the command palette) still find it and a news bubble sees that its target is not on
 * show. The overflow button is the toolbar's last child and only shows when something is hidden. Separators never end
 * the bar (a trailing one is left without width, with no gap) nor open the popup. A label that names a control
 * ({@link JLabel#setLabelFor}) leaves the bar with it, never stays alone. {@code gap} (read at each layout, so it
 * follows the interface size) goes between the components on the bar; the toolbar has none.
 */
final class OverflowToolBarLayout implements LayoutManager {

    private final JButton overflowButton;
    private final IntSupplier gap;
    private List<Component> hidden = List.of();

    OverflowToolBarLayout(JButton overflowButton) {
        this(overflowButton, () -> 0);
    }

    OverflowToolBarLayout(JButton overflowButton, IntSupplier gap) {
        this.overflowButton = overflowButton;
        this.gap = gap;
    }

    /** The components left out of the bar by the last layout, in the bar's order (separators and labels included). */
    List<Component> hidden() {
        return hidden;
    }

    /** A fresh popup with the hidden controls, following their buttons live while it exists. */
    JPopupMenu overflowMenu() {
        return ToolbarOverflowMenu.create(hidden);
    }

    /** Opens the popup under the overflow button, its right edge on the button's. */
    void showMenu() {
        JPopupMenu menu = overflowMenu();
        if (menu.getComponentCount() == 0) {
            return;
        }
        int left = Math.min(0, overflowButton.getWidth() - menu.getPreferredSize().width);
        menu.show(overflowButton, left, overflowButton.getHeight());
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
    }

    @Override
    public void removeLayoutComponent(Component component) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            List<Component> shown = new ArrayList<>();
            int height = 0;
            for (Component component : parent.getComponents()) {
                if (component.isVisible() && component != overflowButton) {
                    shown.add(component);
                    height = Math.max(height, component.getPreferredSize().height);
                }
            }
            return new Dimension(widthOf(shown) + insets.left + insets.right, height + insets.top + insets.bottom);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int first = 0;
            int height = overflowButton.getPreferredSize().height;
            for (Component component : parent.getComponents()) {
                if (component.isVisible() && component != overflowButton) {
                    first = first == 0 ? component.getPreferredSize().width : first;
                    height = Math.max(height, component.getPreferredSize().height);
                }
            }
            return new Dimension(first + overflowButton.getPreferredSize().width + insets.left + insets.right,
                    height + insets.top + insets.bottom);
        }
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int available = Math.max(0, parent.getWidth() - insets.left - insets.right);
            int top = insets.top;
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            List<Component> left = new ArrayList<>();
            for (Component component : parent.getComponents()) {
                if (component != overflowButton && component.isVisible()) {
                    left.add(component);
                }
            }
            int overflowWidth = overflowButton.getPreferredSize().width;
            boolean fits = widthOf(left) <= available;
            int kept = fits ? left.size() : leftThatFits(left, available - overflowWidth - gap.getAsInt());
            hidden = List.copyOf(left.subList(kept, left.size()));
            int shown = kept;
            while (shown > 0 && left.get(shown - 1) instanceof JToolBar.Separator) {
                shown--; // a separator ending the bar would hang there alone
            }
            int next = place(left.subList(0, shown), insets.left, top, height);
            if (shown > 0 && !fits) {
                next += gap.getAsInt();
            }
            for (Component component : left.subList(shown, left.size())) {
                component.setBounds(next, top, 0, 0);
            }
            // the » sits right after what stays, so hiding leaves no gap
            showOverflow(!fits, next, top, height, overflowWidth);
        }
    }

    /**
     * How many components of {@code left}, from the start, fit in {@code room} (never ending with a separator, nor
     * with a label whose control leaves).
     */
    private int leftThatFits(List<Component> left, int room) {
        int kept = 0;
        int used = 0;
        while (kept < left.size()
                && used + (kept > 0 ? gap.getAsInt() : 0) + left.get(kept).getPreferredSize().width <= room) {
            used += (kept > 0 ? gap.getAsInt() : 0) + left.get(kept).getPreferredSize().width;
            kept++;
        }
        while (kept > 0 && (left.get(kept - 1) instanceof JToolBar.Separator
                || left.get(kept - 1) instanceof JLabel label && label.getLabelFor() != null
                && left.subList(kept, left.size()).contains(label.getLabelFor()))) {
            kept--;
        }
        return kept;
    }

    private void showOverflow(boolean show, int left, int top, int height, int width) {
        if (overflowButton.isVisible() != show) {
            overflowButton.setVisible(show);
        }
        if (show) {
            Dimension size = overflowButton.getPreferredSize();
            overflowButton.setBounds(left, top + (height - size.height) / 2, width, size.height);
        } else {
            overflowButton.setBounds(left, top, 0, 0);
        }
    }

    private int widthOf(List<Component> components) {
        int width = 0;
        for (Component component : components) {
            width += component.getPreferredSize().width;
        }
        return width + Math.max(0, components.size() - 1) * gap.getAsInt();
    }

    /**
     * Puts {@code components} side by side from {@code left}, {@code gap} apart, centred on the bar; returns where the
     * last one ends.
     */
    private int place(List<Component> components, int left, int top, int height) {
        int next = left;
        for (int index = 0; index < components.size(); index++) {
            Component component = components.get(index);
            next += index > 0 ? gap.getAsInt() : 0;
            Dimension size = component.getPreferredSize();
            component.setBounds(next, top + (height - size.height) / 2, size.width, size.height);
            next += size.width;
        }
        return next;
    }

    /** Lets {@code onHide} run when {@code menu} closes (cancelled or after a choice). */
    static void whenClosed(JPopupMenu menu, Runnable onHide) {
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                onHide.run();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
            }
        });
    }
}
