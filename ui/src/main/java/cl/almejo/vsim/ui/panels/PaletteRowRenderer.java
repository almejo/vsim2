package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PalettePanel.TopSection;
import cl.almejo.vsim.ui.panels.PalettePanel.UserComponent;
import cl.almejo.vsim.ui.panels.PalettePanel.UserSection;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;

/** Draws the palette rows: component icon and name, the section headers and the favourite stars. */
final class PaletteRowRenderer extends DefaultTreeCellRenderer {
    private final PalettePanel panel;

    PaletteRowRenderer(PalettePanel panel) {
        this.panel = panel;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree owner, Object value, boolean selected, boolean expanded,
                                                  boolean leaf, int row, boolean focused) {
        super.getTreeCellRendererComponent(owner, value, selected, expanded, leaf, row, focused);
        Object item = ((DefaultMutableTreeNode) value).getUserObject();
        setEnabled(owner.isEnabled() && item != TopSection.EMPTY);
        getAccessibleContext().setAccessibleName(PalettePanel.isEntryItem(item) ? panel.accessibleName(item) : null);
        if (item instanceof PaletteEntry entry) {
            setText(entry.name());
            setIcon(panel.icon(entry));
            setToolTipText(PalettePanel.help(entry));
        } else if (item instanceof UserComponent component) {
            setText(Messages.componentName(component.type()));
            setIcon(panel.icon(component.type()));
            setToolTipText(null);
        } else if (item instanceof TopSection section) {
            setText(Messages.text(switch (section) {
                case FAVORITES -> "palette.favorites";
                case EMPTY -> "palette.favorites.empty";
                case RECENT -> "palette.recent";
            }));
            setIcon(switch (section) {
                case FAVORITES -> new FavoriteStar(true, selected);
                case EMPTY -> null;
                case RECENT -> new RecentIcon(selected);
            });
            setToolTipText(null);
        } else if (item instanceof UserSection section) {
            setText(section.title());
            setIcon(null);
            setToolTipText(null);
        } else if (item instanceof ComponentCategory category) {
            setText(Messages.categoryName(category));
            setIcon(null);
            setToolTipText(null);
        }
        return this;
    }
}
