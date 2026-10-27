package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Right-click menu of the canvas: the basic M2 entries, then one group per {@link ContextMenuContributor}. On a
 * component and on the empty canvas the {@link ContextMenuEntry tagged} entries are then laid out in the order of
 * spec §6.6, and any other items follow.
 */
public final class ContextMenuFactory {

    /** Spec §6.6 for a component: Configurar…, Etiqueta…, Rotar ↻ / ↺, Cortar, Copiar, Duplicar, Borrar. */
    private static final List<List<ContextMenuEntry>> COMPONENT_LAYOUT = List.of(
            List.of(ContextMenuEntry.VALUE, ContextMenuEntry.CONFIGURE, ContextMenuEntry.LABEL,
                    ContextMenuEntry.RESET_LABEL_POSITION),
            List.of(ContextMenuEntry.ROTATE_CLOCKWISE, ContextMenuEntry.ROTATE_COUNTER_CLOCKWISE),
            List.of(ContextMenuEntry.CUT, ContextMenuEntry.COPY, ContextMenuEntry.DUPLICATE),
            List.of(ContextMenuEntry.DELETE));
    /** Spec §6.6 for the empty canvas: Pegar, Agregar texto aquí, Seleccionar todo, Centrar vista. */
    private static final List<List<ContextMenuEntry>> EMPTY_CANVAS_LAYOUT = List.of(
            List.of(ContextMenuEntry.PASTE, ContextMenuEntry.ADD_TEXT),
            List.of(ContextMenuEntry.SELECT_ALL, ContextMenuEntry.CENTER_VIEW));

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
        if (target.isEmpty()) {
            arrange(menu, EMPTY_CANVAS_LAYOUT);
        } else if (target.get() instanceof Component && !multiSelectionTarget) {
            arrange(menu, COMPONENT_LAYOUT);
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
            menu.add(ContextMenuEntry.CENTER_VIEW.tag(new JMenuItem(actions.centerView())));
            return;
        }
        switch (target.get()) {
            case Component component -> {
                menu.add(ContextMenuEntry.CONFIGURE.tag(new JMenuItem(actions.configure())));
                menu.add(ContextMenuEntry.ROTATE_CLOCKWISE.tag(new JMenuItem(actions.rotateClockwise())));
                menu.add(ContextMenuEntry.ROTATE_COUNTER_CLOCKWISE.tag(
                        new JMenuItem(actions.rotateCounterClockwise())));
                menu.add(ContextMenuEntry.DELETE.tag(new JMenuItem(actions.delete())));
            }
            case Wire wire -> {
                JMenuItem item = new JMenuItem(actions.delete());
                item.setText(Messages.text("action.deleteWire"));
                menu.add(item);
            }
            case TextLabel label -> menu.add(new JMenuItem(actions.delete()));
            case Frame frame -> menu.add(new JMenuItem(actions.delete()));
            case StickyNote note -> menu.add(new JMenuItem(actions.delete()));
        }
    }

    /**
     * Puts the tagged entries of {@code layout} first, in its order, one separated group per inner list; everything
     * else follows in the order and groups it was added in.
     */
    private static void arrange(JPopupMenu menu, List<List<ContextMenuEntry>> layout) {
        Map<ContextMenuEntry, java.awt.Component> tagged = new EnumMap<>(ContextMenuEntry.class);
        Set<ContextMenuEntry> laidOut = EnumSet.noneOf(ContextMenuEntry.class);
        layout.forEach(laidOut::addAll);
        List<java.awt.Component> rest = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            Optional<ContextMenuEntry> entry = ContextMenuEntry.of(child).filter(laidOut::contains);
            if (entry.isPresent() && !tagged.containsKey(entry.get())) {
                tagged.put(entry.get(), child);
            } else {
                rest.add(child);
            }
        }
        menu.removeAll();
        for (List<ContextMenuEntry> group : layout) {
            List<java.awt.Component> present = group.stream().map(tagged::get).filter(Objects::nonNull).toList();
            if (!present.isEmpty() && menu.getComponentCount() > 0) {
                menu.addSeparator();
            }
            present.forEach(menu::add);
        }
        boolean separate = menu.getComponentCount() > 0;
        for (java.awt.Component child : rest) {
            if (child instanceof JSeparator) {
                separate = menu.getComponentCount() > 0;
            } else {
                if (separate) {
                    menu.addSeparator();
                    separate = false;
                }
                menu.add(child);
            }
        }
    }
}
