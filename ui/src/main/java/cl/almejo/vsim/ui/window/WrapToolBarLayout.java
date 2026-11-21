package cl.almejo.vsim.ui.window;

import javax.swing.JToolBar;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays a toolbar out in rows that wrap when it does not fit the width, keeping the order of its components, instead
 * of cutting the last ones off (audit V1: nothing is hidden). The components after the {@code glue} form a group that
 * sits at the right edge of the last row (or of a new row when that one has no room for it). A separator never opens
 * a row. The preferred height follows the rows for the width the toolbar has now; when a layout finds that the
 * height it needs changed, it asks the toolbar to revalidate so the window gives it the new height.
 */
final class WrapToolBarLayout implements LayoutManager {

    private final Component glue;
    private int lastPreferredHeight = -1;

    WrapToolBarLayout(Component glue) {
        this.glue = glue;
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
            int available = parent.getWidth() > 0 ? parent.getWidth() - insets.left - insets.right : Integer.MAX_VALUE;
            Rows rows = rowsFor(parent, available);
            return new Dimension(rows.widestRow + insets.left + insets.right, rows.height + insets.top + insets.bottom);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int widest = 0;
            int height = 0;
            for (Component component : parent.getComponents()) {
                if (component.isVisible() && component != glue) {
                    widest = Math.max(widest, component.getPreferredSize().width);
                    height = Math.max(height, component.getPreferredSize().height);
                }
            }
            return new Dimension(widest + insets.left + insets.right, height + insets.top + insets.bottom);
        }
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int available = Math.max(0, parent.getWidth() - insets.left - insets.right);
            Rows rows = rowsFor(parent, available);
            int top = insets.top;
            for (Row row : rows.rows) {
                int rowHeight = row.height;
                int left = row.rightAligned ? insets.left + available - row.width : insets.left;
                left = place(row.components, left, top, rowHeight);
                if (row.rightGroup != null) {
                    place(row.rightGroup.components, insets.left + available - row.rightGroup.width, top, rowHeight);
                }
                for (Component skipped : row.skippedSeparators) {
                    skipped.setBounds(insets.left, top, 0, 0);
                }
                top += rowHeight;
            }
            int needed = rows.height + insets.top + insets.bottom;
            if (needed != lastPreferredHeight) {
                lastPreferredHeight = needed;
                if (parent.getHeight() != needed) {
                    parent.revalidate();
                }
            }
        }
    }

    /** Puts {@code components} side by side from {@code left}, centred in the row; returns where the next would go. */
    private static int place(List<Component> components, int left, int top, int rowHeight) {
        int next = left;
        for (Component component : components) {
            Dimension size = component.getPreferredSize();
            component.setBounds(next, top + (rowHeight - size.height) / 2, size.width, size.height);
            next += size.width;
        }
        return next;
    }

    private Rows rowsFor(Container parent, int available) {
        List<Component> left = new ArrayList<>();
        List<Component> right = new ArrayList<>();
        boolean afterGlue = false;
        for (Component component : parent.getComponents()) {
            if (component == glue) {
                afterGlue = true;
            } else if (component.isVisible()) {
                (afterGlue ? right : left).add(component);
            }
        }
        List<Row> rows = new ArrayList<>();
        Row current = new Row(false);
        for (Component component : left) {
            int width = component.getPreferredSize().width;
            if (current.components.isEmpty() && component instanceof JToolBar.Separator) {
                current.skippedSeparators.add(component);
                continue;
            }
            if (!current.components.isEmpty() && current.width + width > available) {
                rows.add(current);
                current = new Row(false);
                if (component instanceof JToolBar.Separator) {
                    current.skippedSeparators.add(component);
                    continue;
                }
            }
            current.add(component);
        }
        if (!right.isEmpty()) {
            Row group = new Row(true);
            right.forEach(group::add);
            if (current.width + group.width <= available) {
                current.rightGroup = group;
                current.width += group.width;
            } else {
                rows.add(current);
                current = group;
            }
        }
        rows.add(current);
        return new Rows(rows);
    }

    private static final class Row {
        final boolean rightAligned;
        final List<Component> components = new ArrayList<>();
        final List<Component> skippedSeparators = new ArrayList<>();
        Row rightGroup;
        int width;
        int height;

        Row(boolean rightAligned) {
            this.rightAligned = rightAligned;
        }

        void add(Component component) {
            Dimension size = component.getPreferredSize();
            components.add(component);
            width += size.width;
            height = Math.max(height, size.height);
        }
    }

    private static final class Rows {
        final List<Row> rows;
        final int height;
        final int widestRow;

        Rows(List<Row> rows) {
            this.rows = rows;
            int total = 0;
            int widest = 0;
            for (Row row : rows) {
                if (row.rightGroup != null) {
                    row.height = Math.max(row.height, row.rightGroup.height);
                }
                total += row.height;
                widest = Math.max(widest, row.width);
            }
            this.height = total;
            this.widestRow = widest;
        }
    }
}
