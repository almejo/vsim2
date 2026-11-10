package cl.almejo.vsim.ui.shortcuts.sheet;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.KeyNames;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;

import javax.swing.KeyStroke;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which shortcuts the sheet shows and in which groups: the resource {@value #RESOURCE} (a {@code [GROUP]} line, then
 * one {@link ShortcutCatalog} id per line, or {@value #THE_SHEET} for the sheet's own key). The keys are never in the
 * file: {@link #groups} reads the user's bindings each time, so a rebound shortcut shows its new key and one left
 * without a key is left out.
 */
final class SheetSelection {

    static final String RESOURCE = "/shortcuts/sheet.txt";
    private static final String COMMENT = "#";
    /** The line of the selection that stands for the sheet's own key, «?» (not a catalog shortcut). */
    static final String THE_SHEET = "?";

    /** The sheet's columns, in order. */
    enum Group {
        FILE, EDIT, VIEW, SIMULATE, HELP;

        String title() {
            return switch (this) {
                case FILE -> Messages.text("shortcutSheet.group.file");
                case EDIT -> Messages.text("shortcutSheet.group.edit");
                case VIEW -> Messages.text("shortcutSheet.group.view");
                case SIMULATE -> Messages.text("shortcutSheet.group.simulate");
                case HELP -> Messages.text("shortcutSheet.group.help");
            };
        }
    }

    /** One line of the selection: the catalog {@code id} shown in {@code group}. */
    record Item(Group group, String id) {
    }

    /**
     * A shortcut as the sheet shows it: its keys in the user's language as text («Ctrl+Mayús+Z») and one by one for
     * the keycaps, and the action's name.
     */
    record Row(String text, List<String> keys, String name) {
        Row {
            keys = List.copyOf(keys);
        }
    }

    /** A titled column of rows. */
    record Column(String title, List<Row> rows) {
        Column {
            rows = List.copyOf(rows);
        }
    }

    private SheetSelection() {
    }

    /** The selection of the resource, in its order. */
    static List<Item> load() {
        try (InputStream stream = SheetSelection.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("missing resource " + RESOURCE);
            }
            return parse(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().toList());
        } catch (IOException exception) {
            throw new UncheckedIOException("could not read " + RESOURCE, exception);
        }
    }

    static List<Item> parse(List<String> lines) {
        List<Item> items = new ArrayList<>();
        Group group = null;
        for (String line : lines) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith(COMMENT)) {
                continue;
            }
            if (text.startsWith("[") && text.endsWith("]")) {
                group = Group.valueOf(text.substring(1, text.length() - 1));
            } else if (group == null) {
                throw new IllegalArgumentException("a shortcut before the first [GROUP]: " + text);
            } else {
                items.add(new Item(group, text));
            }
        }
        return List.copyOf(items);
    }

    /** The columns of {@code items} with the keys of {@code bindings} now; a group left without rows is left out. */
    static List<Column> groups(List<Item> items, KeyBindings bindings) {
        List<Column> columns = new ArrayList<>();
        for (Group group : Group.values()) {
            List<Row> rows = new ArrayList<>();
            for (Item item : items) {
                if (item.group() == group) {
                    rowOf(item.id(), bindings).ifPresent(rows::add);
                }
            }
            if (!rows.isEmpty()) {
                columns.add(new Column(group.title(), rows));
            }
        }
        return List.copyOf(columns);
    }

    private static Optional<Row> rowOf(String id, KeyBindings bindings) {
        if (id.equals(THE_SHEET)) {
            KeyStroke questionMark = ShortcutSheetFeatures.QUESTION_MARK;
            return Optional.of(new Row(KeyNames.describe(questionMark), KeyNames.keys(questionMark,
                    KeyNames.MAC_NOTATION), Messages.text("action.shortcutSheet")));
        }
        Optional<ShortcutEntry> entry = ShortcutCatalog.find(id);
        Optional<KeyStroke> stroke = bindings.strokeFor(id);
        if (entry.isEmpty() || stroke.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Row(KeyNames.describe(stroke.get()),
                KeyNames.keys(stroke.get(), KeyNames.MAC_NOTATION), nameOf(entry.get())));
    }

    /**
     * The action's own name, short: without the «…» of a menu item that opens a dialog nor a closing remark in
     * parentheses («Modo presentación (pantalla completa)» is «Modo presentación» on the sheet).
     */
    private static String nameOf(ShortcutEntry entry) {
        return Messages.text(entry.nameKey()).replaceAll("(…|\\.\\.\\.)\\s*$", "")
                .replaceAll("\\s*\\([^()]*\\)\\s*$", "").strip();
    }
}
