package cl.almejo.vsim.ui.ai.present;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.Arrays;
import java.util.List;

/**
 * A row of buttons sharing its width, counting only the visible ones (a hidden button leaves no hole and no gap,
 * unlike a MigLayout split whose first component is hidden). Every button shows its whole text whatever the font:
 * equal widths when they fit; else one row with each button at least as wide as its text; else one button per row,
 * as wide as the row. How tall it is depends on its width ({@link #heightFor}). EDT only.
 */
final class ButtonRow extends JPanel {

    static final int GAP = 6;

    ButtonRow(Component... buttons) {
        super(new RowLayout());
        setOpaque(false);
        Arrays.stream(buttons).forEach(this::add);
    }

    /** Visible when one of its buttons is. */
    boolean hasVisibleButton() {
        return Arrays.stream(getComponents()).anyMatch(Component::isVisible);
    }

    /** How tall the row is when it is {@code width} wide (its buttons stack when they do not fit side by side). */
    int heightFor(int width) {
        List<Component> shown = visible(this);
        Insets insets = getInsets();
        int rows = fitsInOneRow(shown, width - insets.left - insets.right) ? 1 : shown.size();
        int height = tallest(shown);
        return rows * height + Math.max(0, rows - 1) * GAP + insets.top + insets.bottom;
    }

    private static List<Component> visible(Container parent) {
        return Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList();
    }

    private static int tallest(List<Component> shown) {
        return shown.stream().mapToInt(component -> component.getPreferredSize().height).max().orElse(0);
    }

    private static int gaps(List<Component> shown) {
        return Math.max(0, shown.size() - 1) * GAP;
    }

    private static int sumOfPreferredWidths(List<Component> shown) {
        return shown.stream().mapToInt(component -> component.getPreferredSize().width).sum();
    }

    private static boolean fitsInOneRow(List<Component> shown, int available) {
        return shown.size() <= 1 || sumOfPreferredWidths(shown) + gaps(shown) <= available;
    }

    private static final class RowLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component component) {
        }

        @Override
        public void removeLayoutComponent(Component component) {
        }

        /** Side by side at equal widths; as tall as it is at its current width once it has one. */
        @Override
        public Dimension preferredLayoutSize(Container parent) {
            List<Component> shown = visible(parent);
            int widest = shown.stream().mapToInt(component -> component.getPreferredSize().width).max().orElse(0);
            Insets insets = parent.getInsets();
            int width = shown.size() * widest + gaps(shown) + insets.left + insets.right;
            int height = parent.getWidth() > 0
                    ? ((ButtonRow) parent).heightFor(parent.getWidth())
                    : tallest(shown) + insets.top + insets.bottom;
            return new Dimension(width, height);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Dimension preferred = preferredLayoutSize(parent);
            return new Dimension(0, preferred.height);
        }

        @Override
        public void layoutContainer(Container parent) {
            List<Component> shown = visible(parent);
            if (shown.isEmpty()) {
                return;
            }
            Insets insets = parent.getInsets();
            int width = parent.getWidth() - insets.left - insets.right;
            int height = tallest(shown);
            if (!fitsInOneRow(shown, width)) {
                int y = insets.top;
                for (Component component : shown) {
                    component.setBounds(insets.left, y, Math.max(0, width), height);
                    y += height + GAP;
                }
                return;
            }
            int available = width - gaps(shown);
            int widest = shown.stream().mapToInt(component -> component.getPreferredSize().width).max().orElse(0);
            boolean equal = shown.size() * widest <= available;
            // Equal shares when the widest fits in one; else each its text plus an equal share of what is left.
            int spare = equal ? available : available - sumOfPreferredWidths(shown);
            int x = insets.left;
            for (int index = 0; index < shown.size(); index++) {
                Component component = shown.get(index);
                int share = spare / shown.size() + (index < spare % shown.size() ? 1 : 0);
                int componentWidth = equal ? share : component.getPreferredSize().width + share;
                component.setBounds(x, insets.top, Math.max(0, componentWidth), height);
                x += componentWidth + GAP;
            }
        }
    }
}
