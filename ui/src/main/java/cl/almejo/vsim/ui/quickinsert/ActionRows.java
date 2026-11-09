package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.Scope;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The «Acciones» of one editor view, read from what the user can see now: every visible item of its menu bar (enabled
 * or not, inside submenus too) with its menu path and the key that runs it, then the toolbar buttons no menu has. An
 * action carrying {@link Actions#PALETTE_ENTRIES} adds its extra entries right after its own row («Preferencias ▸
 * General»). An action reachable from two menus («Cerrar pestaña») is listed once, where it is met first. EDT only.
 */
final class ActionRows {

    private final KeyBindings bindings;
    private final Map<Action, String> canvasIds = new IdentityHashMap<>();
    private final Set<Object> listed = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Action> leftOut;
    private final List<ActionRow> rows = new ArrayList<>();

    private ActionRows(EditorView view, Set<Action> leftOut) {
        this.bindings = new KeyBindings(view.services().preferences());
        this.leftOut = leftOut;
        Map<String, Action> actions = ShortcutsExtension.actionsOf(view);
        for (ShortcutEntry entry : ShortcutCatalog.entries()) {
            Action action = actions.get(entry.id());
            if (entry.scope() == Scope.CANVAS && action != null) {
                canvasIds.putIfAbsent(action, entry.id());
            }
        }
    }

    /** The rows of {@code view}, in menu order. */
    static List<ActionRow> of(EditorView view) {
        return of(view, Set.of());
    }

    /** Like {@link #of(EditorView)}, without the items of {@code leftOut} (the palette's own «Buscar una acción…»). */
    static List<ActionRow> of(EditorView view, Set<Action> leftOut) {
        ActionRows reader = new ActionRows(view, Collections.unmodifiableSet(identitySetOf(leftOut)));
        reader.readMenuBar(view.menuBar());
        reader.readToolBar(view.toolBar().getComponents());
        return List.copyOf(reader.rows);
    }

    private static Set<Action> identitySetOf(Set<Action> actions) {
        Set<Action> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(actions);
        return identities;
    }

    private void readMenuBar(JMenuBar menuBar) {
        for (int index = 0; index < menuBar.getMenuCount(); index++) {
            JMenu menu = menuBar.getMenu(index);
            if (menu != null && menu.isVisible()) {
                readMenu(menu, menu.getText());
            }
        }
    }

    private void readMenu(JMenu menu, String path) {
        for (Component child : menu.getMenuComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child instanceof JMenu submenu) {
                readMenu(submenu, Messages.format("commandPalette.path", path, submenu.getText()));
            } else if (child instanceof JMenuItem item) {
                readButton(item, item.getText(), path);
            }
        }
    }

    private void readToolBar(Component[] children) {
        for (Component child : children) {
            if (child.isVisible() && child instanceof AbstractButton button && button.getAction() != null) {
                Object name = button.getAction().getValue(Action.NAME);
                if (name instanceof String text && !text.isBlank()) {
                    readButton(button, text, Messages.text("commandPalette.toolbar"));
                }
            }
        }
    }

    /** Adds the row of {@code button} unless its action is already listed, left out or it has no text. */
    private void readButton(AbstractButton button, String name, String path) {
        Action action = button.getAction();
        Object identity = action != null ? action : button;
        if (name == null || name.isBlank() || leftOut.contains(action) || !listed.add(identity)) {
            return;
        }
        boolean enabled = button.isEnabled();
        rows.add(new ActionRow(name, path, shortcutOf(button).map(KeyBindings::describe), enabled,
                enabled ? Optional.empty() : Optional.of(reasonOf(action)), selectedOf(button),
                Actions.unnamed(button::isEnabled, () -> button.doClick(0))));
        if (action != null) {
            extraEntriesOf(action).forEach(extra -> addExtra(extra, path));
        }
    }

    private void addExtra(Action extra, String path) {
        Object name = extra.getValue(Action.NAME);
        if (name instanceof String text && !text.isBlank() && listed.add(extra)) {
            rows.add(new ActionRow(text, path, Optional.empty(), extra.isEnabled(),
                    extra.isEnabled() ? Optional.empty() : Optional.of(reasonOf(extra)), Optional.empty(), extra));
        }
    }

    private static List<Action> extraEntriesOf(Action action) {
        if (!(action.getValue(Actions.PALETTE_ENTRIES) instanceof List<?> entries)) {
            return List.of();
        }
        return entries.stream().filter(Action.class::isInstance).map(Action.class::cast).toList();
    }

    /** The menu accelerator, or for a canvas key (whose item shows none) the key the user gave it. */
    private Optional<KeyStroke> shortcutOf(AbstractButton button) {
        if (button instanceof JMenuItem item && item.getAccelerator() != null) {
            return Optional.of(item.getAccelerator());
        }
        Action action = button.getAction();
        String canvasId = action == null ? null : canvasIds.get(action);
        return canvasId == null ? Optional.empty() : bindings.strokeFor(canvasId);
    }

    private static String reasonOf(Action action) {
        Object description = action == null ? null : action.getValue(Action.SHORT_DESCRIPTION);
        return description instanceof String text && !text.isBlank() ? text
                : Messages.text("commandPalette.notAvailable");
    }

    private static Optional<Boolean> selectedOf(AbstractButton button) {
        boolean checkable = button instanceof JCheckBoxMenuItem || button instanceof JRadioButtonMenuItem
                || button instanceof JToggleButton;
        return checkable ? Optional.of(button.isSelected()) : Optional.empty();
    }
}
