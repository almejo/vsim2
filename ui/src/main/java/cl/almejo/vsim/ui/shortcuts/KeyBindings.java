package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The shortcut of every catalog action: its default unless the user changed it. Overrides live in
 * {@link AppPreferences#shortcutsNode()} (catalog id → {@link #format}, {@code ""} = none); a value that
 * does not parse, is reserved or is a plain key on a menu action ({@link #isAllowedFor}) is ignored and logged once. One stroke belongs to one action at most: {@link #assign}
 * and {@link #reset} take it away from whichever action had it. Every write ends with
 * {@link AppPreferences#shortcutsChanged()}, so every window re-applies. EDT only.
 */
public final class KeyBindings {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyBindings.class);
    private static final int COMMAND_MODIFIERS = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;
    private static final Set<KeyStroke> RESERVED = reservedStrokes(ShortcutCatalog.menuMask());
    /** How {@link #format} writes a key known only by its extended code. */
    private static final String EXTENDED_KEY_PREFIX = "EXTENDED_";

    private final AppPreferences preferences;
    private final Set<String> reported = new HashSet<>();

    public KeyBindings(AppPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        migrateRetiredIds();
    }

    /** The ids the catalog dropped, with the one that does their action now. */
    private static final Map<String, String> RETIRED = Map.of("file.close", "window.closeWindow");

    /**
     * A shortcut saved for an id the catalog no longer has («Cerrar ventana» was {@code file.close}; it is
     * {@code window.closeWindow} now, and Ctrl+W closes the tab) moves to its successor, so a customization is not
     * lost silently. It is dropped, with a warning in the log, when the successor already has a shortcut of its own or
     * the stroke is no longer valid or belongs to another action; a cleared one stays cleared.
     */
    private void migrateRetiredIds() {
        Preferences node = preferences.shortcutsNode();
        for (Map.Entry<String, String> retired : RETIRED.entrySet()) {
            String stored = node.get(retired.getKey(), null);
            if (stored == null) {
                continue;
            }
            node.remove(retired.getKey());
            ShortcutEntry successor = require(retired.getValue());
            if (node.get(successor.id(), null) != null) {
                LOGGER.warn("Dropping the old shortcut {} = \"{}\": {} has its own", retired.getKey(), stored,
                        successor.id());
                continue;
            }
            if (stored.isEmpty()) {
                node.put(successor.id(), "");
                continue;
            }
            KeyStroke parsed = parse(stored);
            if (parsed == null || !isAllowedFor(successor, parsed) || conflictFor(parsed, successor.id()).isPresent()) {
                LOGGER.warn("Dropping the old shortcut {} = \"{}\": it is not valid for {} or another action uses it",
                        retired.getKey(), stored, successor.id());
                continue;
            }
            store(successor, Optional.of(parsed));
        }
    }

    /** The current stroke of {@code id}; empty for no shortcut or an id the catalog does not have. */
    public Optional<KeyStroke> strokeFor(String id) {
        Optional<ShortcutEntry> entry = ShortcutCatalog.find(id);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        String stored = preferences.shortcutsNode().get(id, null);
        if (stored == null) {
            return entry.get().defaultStroke();
        }
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        KeyStroke parsed = parse(stored);
        if (parsed == null || !isAllowedFor(entry.get(), parsed)) {
            if (reported.add(id)) {
                LOGGER.warn("Ignoring the stored shortcut {} = \"{}\"; using its default", id, stored);
            }
            return entry.get().defaultStroke();
        }
        return Optional.of(parsed);
    }

    /** The action other than {@code exceptId} whose current stroke is {@code stroke}, if any. */
    public Optional<ShortcutEntry> conflictFor(KeyStroke stroke, String exceptId) {
        for (ShortcutEntry entry : ShortcutCatalog.entries()) {
            if (!entry.id().equals(exceptId) && strokeFor(entry.id()).filter(stroke::equals).isPresent()) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * Gives {@code stroke} to {@code id}; the action that had it is left without a shortcut. A stroke that
     * {@link #isAllowedFor} refuses (reserved, or a plain key for a menu action) is refused.
     */
    public void assign(String id, KeyStroke stroke) {
        ShortcutEntry entry = require(id);
        Objects.requireNonNull(stroke, "stroke");
        if (!isAllowedFor(entry, stroke)) {
            throw new IllegalArgumentException("stroke not allowed for " + id + ": " + stroke);
        }
        conflictFor(stroke, id).ifPresent(other -> store(other, Optional.empty()));
        store(entry, Optional.of(stroke));
        preferences.shortcutsChanged();
    }

    /** Leaves {@code id} without a shortcut. */
    public void clear(String id) {
        store(require(id), Optional.empty());
        preferences.shortcutsChanged();
    }

    /** Gives {@code id} its default back, taking that stroke away from another action that had it. */
    public void reset(String id) {
        ShortcutEntry entry = require(id);
        entry.defaultStroke().flatMap(stroke -> conflictFor(stroke, id))
                .ifPresent(other -> store(other, Optional.empty()));
        preferences.shortcutsNode().remove(id);
        preferences.shortcutsChanged();
    }

    /** Every action back to its default. */
    public void resetAll() {
        try {
            preferences.shortcutsNode().clear();
        } catch (BackingStoreException exception) {
            LOGGER.warn("Could not forget the keyboard shortcuts", exception);
        }
        preferences.shortcutsChanged();
    }

    /** Esc, Enter, Espacio, Tab, Mayús+Tab, Retroceso and the fixed zoom aliases (spec §5.2) are never assignable. */
    public static boolean isReserved(KeyStroke stroke) {
        return RESERVED.contains(stroke);
    }

    /**
     * Whether {@code entry} may take {@code stroke}: not reserved, and a menu action (WINDOW scope) needs Ctrl, Alt,
     * ⌘ or AltGr unless the key is F1–F24 — a menu accelerator also fires while the user types in a text field of the
     * window. Canvas actions act only while the canvas has the focus, so they take plain keys.
     */
    public static boolean isAllowedFor(ShortcutEntry entry, KeyStroke stroke) {
        return !isReserved(stroke) && !needsModifier(entry, stroke);
    }

    /** A menu action offered a key without Ctrl/Alt/⌘/AltGr that is not a function key. */
    public static boolean needsModifier(ShortcutEntry entry, KeyStroke stroke) {
        return entry.scope() == Scope.WINDOW && (stroke.getModifiers() & COMMAND_MODIFIERS) == 0
                && !isFunctionKey(stroke.getKeyCode());
    }

    private static boolean isFunctionKey(int keyCode) {
        return (keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12)
                || (keyCode >= KeyEvent.VK_F13 && keyCode <= KeyEvent.VK_F24);
    }

    private ShortcutEntry require(String id) {
        return ShortcutCatalog.find(id).orElseThrow(() -> new IllegalArgumentException("not a shortcut id: " + id));
    }

    /** An override equal to the default is not stored, so the default can change in a later version. */
    private void store(ShortcutEntry entry, Optional<KeyStroke> stroke) {
        Preferences node = preferences.shortcutsNode();
        if (stroke.equals(entry.defaultStroke())) {
            node.remove(entry.id());
        } else {
            node.put(entry.id(), stroke.map(KeyBindings::format).orElse(""));
        }
    }

    /**
     * The stored form of {@code stroke}: {@code KeyStroke.toString()}, except for a key with no {@code VK_} constant
     * (º on a Spanish layout on Linux), which that form writes as {@code UNKNOWN} and cannot read back: it is written
     * {@value #EXTENDED_KEY_PREFIX} and its extended code in hexadecimal ({@code "shift pressed EXTENDED_10000E7"}).
     */
    static String format(KeyStroke stroke) {
        String text = stroke.toString();
        if (KeyCodes.extendedCharacter(stroke.getKeyCode()).isEmpty()) {
            return text;
        }
        return text.substring(0, text.lastIndexOf(' ') + 1) + EXTENDED_KEY_PREFIX
                + Integer.toHexString(stroke.getKeyCode()).toUpperCase(Locale.ROOT);
    }

    private static KeyStroke parse(String stored) {
        try {
            KeyStroke stroke = parseExtended(stored).orElseGet(() -> KeyStroke.getKeyStroke(stored));
            return stroke != null && stroke.getKeyEventType() == KeyEvent.KEY_PRESSED ? stroke : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** A stroke {@link #format} wrote with an extended key code, read back: its modifiers parsed on a stand-in key. */
    private static Optional<KeyStroke> parseExtended(String stored) {
        int keyStart = stored.lastIndexOf(' ') + 1;
        if (!stored.startsWith(EXTENDED_KEY_PREFIX, keyStart)) {
            return Optional.empty();
        }
        int keyCode = Integer.parseUnsignedInt(stored.substring(keyStart + EXTENDED_KEY_PREFIX.length()), 16);
        KeyStroke modifiers = KeyStroke.getKeyStroke(stored.substring(0, keyStart) + "A");
        if (modifiers == null || KeyCodes.extendedCharacter(keyCode).isEmpty()) {
            throw new IllegalArgumentException("not a stored extended key: " + stored);
        }
        return Optional.of(KeyStroke.getKeyStroke(keyCode, modifiers.getModifiers(), false));
    }

    private static Set<KeyStroke> reservedStrokes(int menu) {
        return Set.of(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_ADD, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, menu),
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menu | InputEvent.SHIFT_DOWN_MASK));
    }
}
