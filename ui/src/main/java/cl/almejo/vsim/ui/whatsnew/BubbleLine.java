package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The line under a bubble's text (mockup v4): the key the user gave its action now («Atajo» and the keycaps), else
 * where the action is in the menus («En el menú: Ver ▸ Mostrar los atajos al usarlos»), else no line.
 */
sealed interface BubbleLine {

    /** «Atajo» and these keys, one keycap each. */
    record Shortcut(List<String> keys) implements BubbleLine {

        public Shortcut {
            keys = List.copyOf(keys);
        }
    }

    /** «En el menú:» and this path. */
    record MenuPath(String path) implements BubbleLine {

        public MenuPath {
            Objects.requireNonNull(path, "path");
        }
    }

    /** The line of {@code action} in {@code view} now, read from the user's bindings and the menu bar. */
    static Optional<BubbleLine> of(EditorView view, Optional<NewsStep.ActionReference> action) {
        if (action.isEmpty()) {
            return Optional.empty();
        }
        NewsStep.ActionReference reference = action.get();
        if (NewsText.isFixedKey(reference.shortcutId())) { // «?»: a key nobody can rebind, shown as it is
            return NewsText.strokeOf(reference.shortcutId(), new KeyBindings(view.services().preferences()))
                    .map(stroke -> new Shortcut(KeyNames.keys(stroke, KeyNames.MAC_NOTATION)));
        }
        if (reference.paletteEntry().isEmpty()) {
            Optional<BubbleLine> shortcut = new KeyBindings(view.services().preferences())
                    .strokeFor(reference.shortcutId())
                    .map(stroke -> new Shortcut(KeyNames.keys(stroke, KeyNames.MAC_NOTATION)));
            if (shortcut.isPresent()) {
                return shortcut;
            }
        }
        Action owner = view.shortcutActions().get(reference.shortcutId());
        if (owner == null) {
            return Optional.empty();
        }
        if (reference.paletteEntry().isEmpty()) {
            return MenuPaths.pathOf(view.menuBar(), owner).map(MenuPath::new);
        }
        // a palette entry («Preferencias ▸ General») is written after the menus that hold its action («Archivo»)
        Optional<String> entryName = MenuPaths.paletteEntryName(owner, reference.paletteEntry().get());
        return MenuPaths.parentOf(view.menuBar(), owner)
                .flatMap(parent -> entryName.map(name -> new MenuPath(MenuPaths.join(parent, name))));
    }

    /** Paths in the menu bar, written as the command palette writes them («Archivo ▸ Preferencias…»). */
    final class MenuPaths {

        private MenuPaths() {
        }

        static String join(String parent, String child) {
            return Messages.format("commandPalette.path", parent, child);
        }

        /** «Ver ▸ Mostrar los atajos al usarlos»: the menus down to the visible item of {@code action}. */
        static Optional<String> pathOf(JMenuBar menuBar, Action action) {
            return parentOf(menuBar, action).flatMap(parent -> itemOf(menuBar, action)
                    .map(item -> join(parent, item.getText())));
        }

        /** «Archivo»: the menus that hold the visible item of {@code action}. */
        static Optional<String> parentOf(JMenuBar menuBar, Action action) {
            for (int index = 0; index < menuBar.getMenuCount(); index++) {
                JMenu menu = menuBar.getMenu(index);
                if (menu != null && menu.isVisible()) {
                    Optional<String> found = parentIn(menu, menu.getText(), action);
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
            return Optional.empty();
        }

        private static Optional<String> parentIn(JMenu menu, String path, Action action) {
            for (Component child : menu.getMenuComponents()) {
                if (!child.isVisible()) {
                    continue;
                }
                if (child instanceof JMenu submenu) {
                    Optional<String> found = parentIn(submenu, join(path, submenu.getText()), action);
                    if (found.isPresent()) {
                        return found;
                    }
                } else if (child instanceof JMenuItem item && item.getAction() == action) {
                    return Optional.of(path);
                }
            }
            return Optional.empty();
        }

        private static Optional<JMenuItem> itemOf(JMenuBar menuBar, Action action) {
            for (int index = 0; index < menuBar.getMenuCount(); index++) {
                JMenu menu = menuBar.getMenu(index);
                if (menu != null) {
                    Optional<JMenuItem> found = itemIn(menu, action);
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
            return Optional.empty();
        }

        private static Optional<JMenuItem> itemIn(JMenu menu, Action action) {
            for (Component child : menu.getMenuComponents()) {
                if (child instanceof JMenu submenu) {
                    Optional<JMenuItem> found = itemIn(submenu, action);
                    if (found.isPresent()) {
                        return found;
                    }
                } else if (child instanceof JMenuItem item && item.getAction() == action && child.isVisible()) {
                    return Optional.of(item);
                }
            }
            return Optional.empty();
        }

        /** The name of the command-palette entry of {@code action} with the id {@code entryId} («Preferencias ▸ General»). */
        static Optional<String> paletteEntryName(Action action, String entryId) {
            if (!(action.getValue(Actions.PALETTE_ENTRIES) instanceof List<?> entries)) {
                return Optional.empty();
            }
            return entries.stream().filter(Action.class::isInstance).map(Action.class::cast)
                    .filter(entry -> entryId.equals(entry.getValue(Actions.PALETTE_ENTRY_ID)))
                    .map(entry -> entry.getValue(Action.NAME)).filter(String.class::isInstance)
                    .map(String.class::cast).findFirst();
        }
    }
}
