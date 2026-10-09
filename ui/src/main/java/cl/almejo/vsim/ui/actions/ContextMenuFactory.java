package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Right-click menu of the canvas: the basic M2 entries, then one group per {@link ContextMenuContributor}. */
public final class ContextMenuFactory {

    private ContextMenuFactory() {
    }

    /**
     * When {@code target} is part of a selection of more than one element, the type-specific M2 entries are skipped
     * and the contributors provide the multi-selection items. An item whose {@link Action} is already in the menu is
     * dropped, so contributors never duplicate an entry.
     */
    public static JPopupMenu create(Optional<DocumentElement> target, EditorActions actions, Selection selection,
                                    Point2D gridPosition, List<ContextMenuContributor> contributors) {
        JPopupMenu menu = new JPopupMenu();
        boolean multiSelectionTarget = target.isPresent() && selection.size() > 1
                && selection.contains(target.get().id());
        if (!multiSelectionTarget) {
            addBasicEntries(menu, target, actions);
        }
        for (ContextMenuContributor contributor : contributors) {
            int before = menu.getComponentCount();
            contributor.contribute(menu, target, selection, gridPosition);
            removeDuplicateActions(menu, before);
            if (before > 0 && menu.getComponentCount() > before) {
                menu.insert(new JPopupMenu.Separator(), before);
            }
        }
        return menu;
    }

    /** Removes items added from {@code firstNew} on whose Action already appears earlier in the menu. */
    private static void removeDuplicateActions(JPopupMenu menu, int firstNew) {
        Set<Action> present = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int index = 0; index < firstNew; index++) {
            if (menu.getComponent(index) instanceof JMenuItem item && item.getAction() != null) {
                present.add(item.getAction());
            }
        }
        for (int index = firstNew; index < menu.getComponentCount(); ) {
            if (menu.getComponent(index) instanceof JMenuItem item && item.getAction() != null
                    && !present.add(item.getAction())) {
                menu.remove(index);
            } else {
                index++;
            }
        }
    }

    private static void addBasicEntries(JPopupMenu menu, Optional<DocumentElement> target, EditorActions actions) {
        if (target.isEmpty()) {
            menu.add(new JMenuItem(actions.centerView()));
            return;
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
    }
}
