package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The keys of a tip of the day as the user has them now: a {@code {…}} name of its text is a fixed key or the key the
 * user gave that action, drawn as a keycap; an action without a key is named by its place in the menus
 * («Ventana ▸ Dividir ▸ A los lados»), as the bubbles' «En el menú» line does. Read again at every showing, so a
 * rebound shortcut shows at once.
 */
final class TipKeys {

    private TipKeys() {
    }

    /** The keycaps of {@code name} in {@code view} now; empty for an action without a key. */
    static Optional<List<String>> capsOf(String name, EditorView view) {
        return NewsText.strokeOf(name, new KeyBindings(view.services().preferences()))
                .map(stroke -> KeyNames.keys(stroke, KeyNames.MAC_NOTATION));
    }

    /** {@code text} with its keys as keycaps, or as the menu path of an action without a key. */
    static List<FlowText.Item> items(String text, EditorView view) {
        List<FlowText.Item> items = new ArrayList<>();
        for (NewsText.Part part : NewsText.parse(text)) {
            switch (part) {
                case NewsText.Plain plain -> items.add(new FlowText.Words(plain.text()));
                case NewsText.Key key -> items.add(capsOf(key.name(), view).<FlowText.Item>map(FlowText.Keys::new)
                        .orElseGet(() -> new FlowText.Words(placeOf(key.name(), view))));
            }
        }
        return List.copyOf(items);
    }

    /** Where the action {@code shortcutId} sits in the menus, or its name when no menu holds it. */
    private static String placeOf(String shortcutId, EditorView view) {
        Action action = ShortcutsExtension.actionsOf(view).get(shortcutId);
        Optional<String> path = action == null ? Optional.empty()
                : BubbleLine.MenuPaths.pathOf(view.menuBar(), action);
        return path.orElseGet(() -> ShortcutCatalog.find(shortcutId).map(ShortcutEntry::nameKey).map(Messages::text)
                .orElse(shortcutId));
    }
}
