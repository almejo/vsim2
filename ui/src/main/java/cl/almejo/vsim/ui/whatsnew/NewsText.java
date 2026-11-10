package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text of a bubble with its keys cut out: «{space} cambia el codo» is a key and a plain part. A key is a fixed key
 * of a gesture ({@code {escape}}, {@code {space}}, {@code {backspace}}) or a {@code ShortcutCatalog} id, which shows the
 * key the user gave that action now; the bubble draws each key as a keycap with the names of the user's language.
 */
final class NewsText {

    /** A piece of a bubble's text. */
    sealed interface Part permits Plain, Key {
    }

    /** Words, as written. */
    record Plain(String text) implements Part {

        Plain {
            Objects.requireNonNull(text, "text");
        }
    }

    /** A key by its name: a fixed key or a catalog id. */
    record Key(String name) implements Part {

        Key {
            Objects.requireNonNull(name, "name");
        }
    }

    private static final Pattern KEY = Pattern.compile("\\{([A-Za-z][A-Za-z0-9.]*)}");

    /** The keys of gestures, which no catalog entry names (they are fixed in the canvas). */
    private static final Map<String, KeyStroke> FIXED_KEYS = Map.of(
            "escape", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            "space", KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
            "backspace", KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));

    private NewsText() {
    }

    /** {@code text} cut at its {@code {…}} keys, in order. */
    static List<Part> parse(String text) {
        List<Part> parts = new ArrayList<>();
        Matcher matcher = KEY.matcher(text);
        int position = 0;
        while (matcher.find()) {
            if (matcher.start() > position) {
                parts.add(new Plain(text.substring(position, matcher.start())));
            }
            parts.add(new Key(matcher.group(1)));
            position = matcher.end();
        }
        if (position < text.length()) {
            parts.add(new Plain(text.substring(position)));
        }
        return List.copyOf(parts);
    }

    /** Whether {@code name} is a fixed key or a catalog id. */
    static boolean isKnown(String name) {
        return FIXED_KEYS.containsKey(name) || ShortcutCatalog.find(name).isPresent();
    }

    /** The key {@code name} stands for now: the fixed one, or the user's binding of that action (none when unbound). */
    static Optional<KeyStroke> strokeOf(String name, KeyBindings bindings) {
        KeyStroke fixed = FIXED_KEYS.get(name);
        return fixed != null ? Optional.of(fixed) : bindings.strokeFor(name);
    }
}
