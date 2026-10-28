package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.component.VisibleState;

import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.JLabel;
import javax.swing.JTable;
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
    /** How far the address and ASCII columns' text moves from the strong text to the background. */
    private static final float MUTED_WEIGHT = 0.5f;
    private static final float EDITED_WEIGHT = 0.25f;

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
        OptionalLong cellAddress = model.addressAt(row, column);
        boolean edited = cellAddress.isPresent() && model.isEdited(cellAddress.getAsLong());
        setFont(new Font(Font.MONOSPACED, edited ? Font.BOLD : Font.PLAIN, table.getFont().getSize()));
        setHorizontalAlignment(model.isAsciiColumn(column) ? JLabel.LEFT : JLabel.CENTER);
        if (!selected) {
            Color background = table.getBackground();
            setBackground(background);
            if (model.isAddressColumn(column) || model.isAsciiColumn(column)) {
                // muted: the table's own text pulled towards its background, so it follows light and dark
                setForeground(blend(table.getForeground(), background, MUTED_WEIGHT));
            } else {
                setForeground(table.getForeground());
                OptionalLong address = cellAddress;
                if (edited) {
                    setBackground(blend(background, table.getSelectionBackground(), EDITED_WEIGHT));
                }
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
        return blend(base, tint, TINT_WEIGHT);
    }

    private static Color blend(Color base, Color tint, float weight) {
        float keep = 1 - weight;
        return new Color(Math.round(base.getRed() * keep + tint.getRed() * weight),
                Math.round(base.getGreen() * keep + tint.getGreen() * weight),
                Math.round(base.getBlue() * keep + tint.getBlue() * weight));
    }
}
