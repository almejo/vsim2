package cl.almejo.vsim.ui.ai.present;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.util.Arrays;
import java.util.List;

/**
 * The «Presentar» panel's column: the visible children one under the other, each as wide as the container (never
 * wider: a long text ends in «…»), at their preferred heights; when the container is shorter, the one flexible child
 * (the stage cards' scroll pane) gives up the difference, down to its minimum height. Hidden children take no room.
 * Deterministic where MigLayout's scroll-pane sizing and hidden split cells were not (the user's first look).
 */
final class StackLayout implements LayoutManager2 {

    private final int gap;
    private Component flexible;

    StackLayout(int gap) {
        this.gap = gap;
    }

    /** {@code component} shrinks when the container is short. */
    void setFlexible(Component component) {
        flexible = component;
    }

    private static List<Component> visible(Container parent) {
        return Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList();
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return size(parent, false);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return size(parent, true);
    }

    private Dimension size(Container parent, boolean minimum) {
        List<Component> shown = visible(parent);
        int width = 0;
        int height = Math.max(0, shown.size() - 1) * gap;
        for (Component child : shown) {
            Dimension preferred = child.getPreferredSize();
            width = Math.max(width, preferred.width);
            height += minimum && child == flexible ? child.getMinimumSize().height : preferred.height;
        }
        Insets insets = parent.getInsets();
        return new Dimension((minimum ? 0 : width) + insets.left + insets.right,
                height + insets.top + insets.bottom);
    }

    @Override
    public void layoutContainer(Container parent) {
        List<Component> shown = visible(parent);
        Insets insets = parent.getInsets();
        int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
        int available = parent.getHeight() - insets.top - insets.bottom;
        int wanted = preferredLayoutSize(parent).height - insets.top - insets.bottom;
        int shortfall = Math.max(0, wanted - available);
        int y = insets.top;
        for (Component child : shown) {
            int height = child.getPreferredSize().height;
            if (child == flexible && shortfall > 0) {
                height = Math.max(child.getMinimumSize().height, height - shortfall);
            }
            height = Math.max(0, Math.min(height, insets.top + available - y));
            child.setBounds(insets.left, y, width, height);
            y += height + gap;
        }
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
    }

    @Override
    public void addLayoutComponent(Component component, Object constraints) {
    }

    @Override
    public void removeLayoutComponent(Component component) {
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        return 0;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return 0;
    }

    @Override
    public void invalidateLayout(Container target) {
    }
}
