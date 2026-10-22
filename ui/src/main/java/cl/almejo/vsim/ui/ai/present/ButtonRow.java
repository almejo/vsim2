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
 * A row of buttons sharing its width equally, counting only the visible ones (a hidden button leaves no hole and no
 * gap, unlike a MigLayout split whose first component is hidden). EDT only.
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

    private static final class RowLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component component) {
        }

        @Override
        public void removeLayoutComponent(Component component) {
        }

        private static List<Component> visible(Container parent) {
            return Arrays.stream(parent.getComponents()).filter(Component::isVisible).toList();
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            List<Component> shown = visible(parent);
            int width = 0;
            int height = 0;
            for (Component component : shown) {
                Dimension preferred = component.getPreferredSize();
                width = Math.max(width, preferred.width);
                height = Math.max(height, preferred.height);
            }
            Insets insets = parent.getInsets();
            int count = shown.size();
            return new Dimension(count * width + Math.max(0, count - 1) * GAP + insets.left + insets.right,
                    height + insets.top + insets.bottom);
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
            int available = parent.getWidth() - insets.left - insets.right - (shown.size() - 1) * GAP;
            int height = parent.getHeight() - insets.top - insets.bottom;
            int x = insets.left;
            for (int index = 0; index < shown.size(); index++) {
                int width = available / shown.size() + (index < available % shown.size() ? 1 : 0);
                shown.get(index).setBounds(x, insets.top, Math.max(0, width), height);
                x += width + GAP;
            }
        }
    }
}
