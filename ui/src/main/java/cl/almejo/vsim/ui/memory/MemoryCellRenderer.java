package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.VisibleState;

import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Font;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * Paints the grid: monospaced cells, the address and ASCII columns dimmed, and the cell of the last read (blue) or
 * write (orange) tinted. Every colour is read from the look and feel and the colour scheme when the cell is painted, never kept.
 */
final class MemoryCellRenderer extends DefaultTableCellRenderer {

    private static final float TINT_WEIGHT = 0.45f;

    private final MemoryGridModel model;
    private final Supplier<ColorScheme> scheme;

    MemoryCellRenderer(MemoryGridModel model, Supplier<ColorScheme> scheme) {
        this.model = model;
        this.scheme = scheme;
    }

    @Override
    public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                            boolean focused, int row, int column) {
        super.getTableCellRendererComponent(table, value, selected, focused, row, column);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, table.getFont().getSize()));
        setHorizontalAlignment(model.isAsciiColumn(column) ? JLabel.LEFT : JLabel.CENTER);
        if (!selected) {
            Color background = table.getBackground();
            setBackground(background);
            if (model.isAddressColumn(column) || model.isAsciiColumn(column)) {
                Color dimmed = UIManager.getColor("Label.disabledForeground");
                setForeground(dimmed == null ? Color.GRAY : dimmed);
            } else {
                setForeground(table.getForeground());
                OptionalLong address = model.addressAt(row, column);
                if (address.isPresent()) {
                    VisibleState.MemoryAccess access = model.accessAt(address.getAsLong());
                    if (access != VisibleState.MemoryAccess.NONE) {
                        ColorScheme current = scheme.get();
                        setBackground(blend(background, access == VisibleState.MemoryAccess.READ
                                ? current.memoryRead() : current.memoryWrite()));
                    }
                }
            }
        }
        return this;
    }

    private static Color blend(Color base, Color tint) {
        float keep = 1 - TINT_WEIGHT;
        return new Color(Math.round(base.getRed() * keep + tint.getRed() * TINT_WEIGHT),
                Math.round(base.getGreen() * keep + tint.getGreen() * TINT_WEIGHT),
                Math.round(base.getBlue() * keep + tint.getBlue() * TINT_WEIGHT));
    }
}
