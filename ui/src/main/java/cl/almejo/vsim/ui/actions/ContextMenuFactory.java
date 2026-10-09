package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.Optional;

/** Right-click menu of the canvas (basic M2 set; the full menu arrives in M3). */
public final class ContextMenuFactory {

    private ContextMenuFactory() {
    }

    public static JPopupMenu create(Optional<DocumentElement> target, EditorActions actions) {
        JPopupMenu menu = new JPopupMenu();
        if (target.isEmpty()) {
            menu.add(new JMenuItem(actions.centerView()));
            return menu;
        }
        switch (target.get()) {
            case Component component -> {
                menu.add(new JMenuItem(actions.configure()));
                menu.add(new JMenuItem(actions.rotateClockwise()));
                menu.add(new JMenuItem(actions.rotateCounterClockwise()));
                menu.addSeparator();
                menu.add(new JMenuItem(actions.delete()));
            }
            case Wire wire -> {
                JMenuItem item = new JMenuItem(actions.delete());
                item.setText(Messages.text("action.deleteWire"));
                menu.add(item);
            }
            case TextLabel label -> menu.add(new JMenuItem(actions.delete()));
        }
        return menu;
    }
}
