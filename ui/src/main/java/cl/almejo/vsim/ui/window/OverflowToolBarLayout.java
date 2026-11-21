package cl.almejo.vsim.ui.window;

import javax.swing.JButton;
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

/**
 * Lays a toolbar out in one row, always (audit V1: the bar never wraps). The components before the {@code glue} form
 * the left group, the ones after it a group at the right edge. When everything does not fit, the components that do
 * not fit leave the bar from the right end of the left group, keeping the order, and the {@code overflowButton} (a
 * «»» at the right edge) shows; it opens a popup with them ({@link ToolbarOverflowMenu}). A right group that would
 * take more than half of the bar overflows too, after the left one. A hidden component keeps being visible and a child
 * of the bar, with empty bounds, so the lists that read the toolbar (the command palette) still find it and a news
 * bubble sees that its target is not on show. The overflow button is the toolbar's last child and only shows when
 * something is hidden. Separators never end the bar nor open the popup.
 */
final class OverflowToolBarLayout implements LayoutManager {

    private final Component glue;
    private final JButton overflowButton;
    private List<Component> hidden = List.of();

    OverflowToolBarLayout(Component glue, JButton overflowButton) {
        this.glue = glue;
        this.overflowButton = overflowButton;
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
            int width = 0;
            int height = 0;
            for (Component component : parent.getComponents()) {
                if (component.isVisible() && component != glue && component != overflowButton) {
                    width += component.getPreferredSize().width;
                    height = Math.max(height, component.getPreferredSize().height);
                }
            }
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int first = 0;
            int height = overflowButton.getPreferredSize().height;
            for (Component component : parent.getComponents()) {
                if (component.isVisible() && component != glue && component != overflowButton) {
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
            List<Component> right = new ArrayList<>();
            boolean afterGlue = false;
            for (Component component : parent.getComponents()) {
                if (component == glue) {
                    afterGlue = true;
                } else if (component != overflowButton && component.isVisible()) {
                    (afterGlue ? right : left).add(component);
                }
            }
            int overflowWidth = overflowButton.getPreferredSize().width;
            boolean fits = widthOf(left) + widthOf(right) <= available;
            int keptLeft = left.size();
            boolean keepRight = true;
            if (!fits) {
                keepRight = widthOf(right) + overflowWidth <= available / 2;
                int room = available - overflowWidth - (keepRight ? widthOf(right) : 0);
                keptLeft = 0;
                int used = 0;
                while (keptLeft < left.size() && used + left.get(keptLeft).getPreferredSize().width <= room) {
                    used += left.get(keptLeft).getPreferredSize().width;
                    keptLeft++;
                }
                while (keptLeft > 0 && left.get(keptLeft - 1) instanceof JToolBar.Separator) {
                    keptLeft--;
                }
            }
            List<Component> nowHidden = new ArrayList<>(left.subList(keptLeft, left.size()));
            if (!keepRight) {
                nowHidden.addAll(right);
            }
            hidden = List.copyOf(nowHidden);
            int next = place(left.subList(0, keptLeft), insets.left, top, height);
            int rightEdge = insets.left + available - (fits ? 0 : overflowWidth);
            if (keepRight) {
                place(right, rightEdge - widthOf(right), top, height);
            }
            for (Component component : hidden) {
                component.setBounds(next, top, 0, 0);
            }
            showOverflow(!fits, rightEdge, top, height, overflowWidth);
        }
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

    private static int widthOf(List<Component> components) {
        int width = 0;
        for (Component component : components) {
            width += component.getPreferredSize().width;
        }
        return width;
    }

    /** Puts {@code components} side by side from {@code left}, centred on the bar; returns where the next would go. */
    private static int place(List<Component> components, int left, int top, int height) {
        int next = left;
        for (Component component : components) {
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
