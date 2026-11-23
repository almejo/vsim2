package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.ui.toolbar.SplitButton;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * The popup behind the toolbar's «»» button (audit V1): the controls that did not fit the bar, in its order.
 * A button becomes a menu item with its icon and text (its tooltip when it has no text; a toggle, a check item);
 * a {@link SplitButton} a submenu with its main action and its modes; a speed selector a submenu with its choices.
 * Labels and other components are left out, except a label marked {@link #SHOWN_IN_MENU} (a line of information, like
 * the place bar's «Se usa en 3 lugares»), shown as a line of text, and a label that names a selector
 * ({@link JLabel#setLabelFor}), whose text titles the selector's submenu («Valores de: …»). Enabled state and selection
 * follow the button while the popup exists ({@link #create} listens, and lets go when the popup closes).
 */
final class ToolbarOverflowMenu {

    /** Client property: a label with it set to {@code true} shows in the popup as a line of text. */
    static final String SHOWN_IN_MENU = "overflowMenu.shownInMenu";

    private ToolbarOverflowMenu() {
    }

    static JPopupMenu create(List<Component> hidden) {
        JPopupMenu menu = new JPopupMenu();
        List<Runnable> detachers = new ArrayList<>();
        boolean separatorPending = false;
        String caption = "";
        for (Component component : hidden) {
            if (component instanceof JToolBar.Separator) {
                separatorPending = menu.getComponentCount() > 0;
                continue;
            }
            if (component instanceof JLabel label && label.isVisible()
                    && Boolean.TRUE.equals(label.getClientProperty(SHOWN_IN_MENU))) {
                menu.add(lineOf(label, menu));
                continue;
            }
            if (component instanceof JLabel label && label.getLabelFor() != null && label.getText() != null) {
                caption = label.getText();
                continue;
            }
            JMenuItem item = component instanceof JComboBox<?> combo && !caption.isBlank() && combo.isVisible()
                    ? submenuOf(combo, caption) : itemFor(component, detachers);
            caption = "";
            if (item == null) {
                continue;
            }
            if (separatorPending) {
                menu.addSeparator();
                separatorPending = false;
            }
            menu.add(item);
        }
        OverflowToolBarLayout.whenClosed(menu, () -> detachers.forEach(Runnable::run));
        return menu;
    }

    private static JMenuItem itemFor(Component component, List<Runnable> detachers) {
        if (!component.isVisible()) {
            return null;
        }
        return switch (component) {
            case SplitButton split -> submenuOf(split, detachers);
            case AbstractButton button -> itemOf(button, detachers);
            case JComboBox<?> combo -> submenuOf(combo);
            default -> null;
        };
    }

    /**
     * A copy of {@code label}'s text in its colour (made readable on the popup's background), lined up with the items'
     * texts: an item that is never highlighted and does nothing, since it is information, not a control.
     */
    private static JMenuItem lineOf(JLabel label, JPopupMenu menu) {
        JMenuItem line = new JMenuItem(label.getText()) {
            @Override
            public void menuSelectionChanged(boolean selected) {
                // never armed: hovering it does not light it as if it did something
            }
        };
        line.setToolTipText(label.getToolTipText());
        Color themeBackground = UIManager.getColor("PopupMenu.background");
        Color background = themeBackground != null ? themeBackground : menu.getBackground();
        line.setForeground(ReadableColors.readableOn(label.getForeground(), background));
        line.setFocusable(false);
        return line;
    }

    /** {@link #submenuOf(JComboBox)} titled «{@code caption}: {@code the chosen item}». */
    private static JMenuItem submenuOf(JComboBox<?> combo, String caption) {
        JMenuItem submenu = submenuOf(combo);
        submenu.setText(Messages.format("overflow.labelledChoice", caption, submenu.getText()));
        return submenu;
    }

    private static JMenuItem itemOf(AbstractButton button, List<Runnable> detachers) {
        JMenuItem item = button instanceof JToggleButton ? new JCheckBoxMenuItem() : new JMenuItem();
        item.setText(textOf(button));
        item.setIcon(iconOf(button));
        item.setToolTipText(button.getToolTipText());
        copyState(button, item);
        ChangeListener follow = event -> copyState(button, item);
        button.addChangeListener(follow);
        detachers.add(() -> button.removeChangeListener(follow));
        item.addActionListener(event -> button.doClick(0));
        return item;
    }

    private static void copyState(AbstractButton button, JMenuItem item) {
        item.setEnabled(button.isEnabled());
        if (item instanceof JCheckBoxMenuItem check) {
            check.setSelected(button.isSelected());
        }
    }

    private static String textOf(AbstractButton button) {
        if (button.getText() != null && !button.getText().isBlank()) {
            return button.getText();
        }
        Action action = button.getAction();
        if (action != null && action.getValue(Action.NAME) instanceof String name && !name.isBlank()) {
            return name;
        }
        return button.getToolTipText() == null ? "" : button.getToolTipText();
    }

    private static Icon iconOf(AbstractButton button) {
        Action action = button.getAction();
        if (action != null && action.getValue(Action.SMALL_ICON) instanceof Icon small) {
            return small;
        }
        return button.getIcon();
    }

    /** The main action first, then the split button's own popup entries (its caption and one radio item per mode). */
    private static JMenuItem submenuOf(SplitButton split, List<Runnable> detachers) {
        AbstractButton main = split.mainButton();
        JMenu submenu = new JMenu(textOf(main));
        submenu.setIcon(main.getIcon());
        submenu.setEnabled(split.isEnabled());
        submenu.add(itemOf(main, detachers));
        submenu.addSeparator();
        for (Component entry : split.popupMenu().getComponents()) {
            submenu.add(entry);
        }
        return submenu;
    }

    /** The closed selector's text as the title and one radio item per speed, the current one marked. */
    private static JMenuItem submenuOf(JComboBox<?> combo) {
        JMenu submenu = new JMenu(rendered(combo, combo.getSelectedItem(), -1));
        submenu.setToolTipText(combo.getToolTipText());
        ButtonGroup group = new ButtonGroup();
        for (int index = 0; index < combo.getItemCount(); index++) {
            Object value = combo.getItemAt(index);
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(rendered(combo, value, index),
                    value.equals(combo.getSelectedItem()));
            item.addActionListener(event -> combo.setSelectedItem(value));
            group.add(item);
            submenu.add(item);
        }
        return submenu;
    }

    @SuppressWarnings("unchecked")
    private static String rendered(JComboBox<?> combo, Object value, int index) {
        Component rendered = ((javax.swing.ListCellRenderer<Object>) combo.getRenderer())
                .getListCellRendererComponent(new JList<>(), value, index, false, false);
        return rendered instanceof JLabel label ? label.getText() : String.valueOf(value);
    }
}
