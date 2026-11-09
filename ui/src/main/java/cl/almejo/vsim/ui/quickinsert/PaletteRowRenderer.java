package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.PaletteEntry;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;

/**
 * Paints the rows of the command palette: a component with its picture and category, a heading in bold, and an action
 * as its name over its menu path (or why it cannot run now, in grey) with its key at the right and a ✓ when it is a
 * check item that is on. Colours are read from the theme at every call.
 */
final class PaletteRowRenderer implements ListCellRenderer<QuickInsertPanel.Row> {

    private static final int ICON_SIZE = 24;
    private final PainterRegistry painters;
    private final Appearance appearance;
    private final DefaultListCellRenderer plain = new DefaultListCellRenderer();
    private final JPanel actionCell = new JPanel(new BorderLayout(8, 0));
    private final JLabel check = new JLabel();
    private final JLabel name = new JLabel();
    private final JLabel path = new JLabel();
    private final JLabel shortcut = new JLabel();

    PaletteRowRenderer(PainterRegistry painters, Appearance appearance) {
        this.painters = painters;
        this.appearance = appearance;
        JPanel texts = new JPanel(new GridLayout(2, 1));
        texts.setOpaque(false);
        texts.add(name);
        texts.add(path);
        path.putClientProperty("FlatLaf.styleClass", "small");
        shortcut.putClientProperty("FlatLaf.styleClass", "small");
        actionCell.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 8));
        actionCell.add(check, BorderLayout.WEST);
        actionCell.add(texts, BorderLayout.CENTER);
        actionCell.add(shortcut, BorderLayout.EAST);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends QuickInsertPanel.Row> list,
                                                  QuickInsertPanel.Row value, int index, boolean selected,
                                                  boolean focused) {
        if (value instanceof QuickInsertPanel.ActionChoice choice) {
            return actionCell(list, choice.row(), selected);
        }
        plain.getListCellRendererComponent(list, value, index, selected, focused);
        if (value instanceof QuickInsertPanel.Choice choice) {
            PaletteEntry entry = choice.entry();
            plain.setText(entry.name() + " · " + entry.category());
            plain.setIcon(new ComponentIcon(entry.type(), painters.painterFor(entry.type().id()),
                    appearance.scheme(), ICON_SIZE, entry.parameters()));
            plain.putClientProperty("FlatLaf.style", null);
        } else if (value instanceof QuickInsertPanel.Heading heading) {
            plain.setText(heading.title());
            plain.setIcon(null);
            plain.putClientProperty("FlatLaf.style", "font: bold");
        }
        return plain;
    }

    private Component actionCell(JList<?> list, ActionRow row, boolean selected) {
        Color background = selected ? list.getSelectionBackground() : list.getBackground();
        Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
        Color muted = selected ? foreground : UIManager.getColor("Label.disabledForeground");
        actionCell.setBackground(background);
        actionCell.setOpaque(true);
        check.setPreferredSize(null);
        check.setText(Messages.text("commandPalette.checked"));
        check.setPreferredSize(check.getPreferredSize()); // the column keeps its width when there is no ✓
        check.setText(row.selected().orElse(false) ? Messages.text("commandPalette.checked") : "");
        check.setForeground(foreground);
        name.setText(row.name());
        name.setForeground(row.enabled() ? foreground : muted);
        path.setText(row.disabledReason()
                .map(reason -> Messages.format("commandPalette.disabledPath", row.menuPath(), reason))
                .orElse(row.menuPath()));
        path.setForeground(muted);
        shortcut.setText(row.shortcut().orElse(""));
        shortcut.setForeground(muted);
        actionCell.getAccessibleContext().setAccessibleName(row.name() + ", " + path.getText()
                + row.shortcut().map(key -> ", " + key).orElse(""));
        return actionCell;
    }
}
