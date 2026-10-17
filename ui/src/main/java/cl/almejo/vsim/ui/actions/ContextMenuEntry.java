package cl.almejo.vsim.ui.actions;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import java.util.Optional;

/**
 * The well-known canvas context-menu entries. Whoever adds one tags its item, so {@link ContextMenuFactory} can lay
 * the menu out in the order of spec §6.6 whatever contributor added it.
 */
public enum ContextMenuEntry {
    VALUE, CONFIGURE, LABEL, RESET_LABEL_POSITION, ROTATE_CLOCKWISE, ROTATE_COUNTER_CLOCKWISE, CUT, COPY, DUPLICATE,
    DELETE, PASTE, ADD_TEXT, SELECT_ALL, CENTER_VIEW;

    private static final String PROPERTY = "vsim.contextMenuEntry";

    /** Marks {@code item} as this entry and returns it. */
    public JMenuItem tag(JMenuItem item) {
        item.putClientProperty(PROPERTY, this);
        return item;
    }

    static Optional<ContextMenuEntry> of(java.awt.Component child) {
        return child instanceof JComponent component
                && component.getClientProperty(PROPERTY) instanceof ContextMenuEntry entry
                ? Optional.of(entry) : Optional.empty();
    }
}
