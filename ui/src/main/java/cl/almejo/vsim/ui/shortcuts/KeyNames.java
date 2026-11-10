package cl.almejo.vsim.ui.shortcuts;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The one place that turns a shortcut into text for the user, in the user's language: «Ctrl+Mayús+S», «Supr»,
 * «Retroceso», «Espacio» in Spanish, «Ctrl+Shift+S», «Delete» in English, and «⇧⌘S» in the macOS notation. Swing's
 * own names ({@link KeyEvent#getKeyText}) follow the JVM's language, not VSim's, and say «Shift» on a Spanish
 * desktop; every place that shows a key (the sheet of shortcuts, the command palette, the tooltips, the shortcuts
 * table, the chips of «Mostrar los atajos que se usan») asks here, so a key has one name everywhere. A stroke of a
 * typed character («?») is that character.
 */
public final class KeyNames {

    /** Whether this platform writes shortcuts in the macOS notation (⌘ and the other symbols, nothing between). */
    public static final boolean MAC_NOTATION = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
            .contains("mac");

    private static final String PLUS = "+";

    private KeyNames() {
    }

    /** {@code stroke} in this platform's notation. */
    public static String describe(KeyStroke stroke) {
        return describe(stroke, MAC_NOTATION);
    }

    /** «Ctrl+Mayús+Z», or «⇧⌘Z» in the macOS notation (⌃ ⌥ ⇧ ⌘ and the key, nothing between). */
    public static String describe(KeyStroke stroke, boolean macNotation) {
        return String.join(macNotation ? "" : PLUS, keys(stroke, macNotation));
    }

    /** The keys of {@code stroke} one by one (to draw each as a keycap): modifiers first, the key last. */
    public static List<String> keys(KeyStroke stroke, boolean macNotation) {
        if (stroke.getKeyEventType() == KeyEvent.KEY_TYPED) {
            return List.of(String.valueOf(stroke.getKeyChar()));
        }
        List<String> keys = new ArrayList<>(macNotation ? macModifiers(stroke.getModifiers())
                : modifiers(stroke.getModifiers()));
        keys.add(keyName(stroke.getKeyCode()));
        return List.copyOf(keys);
    }

    /** Ctrl, Meta, Alt, AltGr, Mayús: the order of «Ctrl+Alt+Mayús+L». */
    private static List<String> modifiers(int modifiers) {
        List<String> names = new ArrayList<>();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            names.add(Messages.text("keyName.control"));
        }
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
            names.add(Messages.text("keyName.meta"));
        }
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            names.add(Messages.text("keyName.alt"));
        }
        if ((modifiers & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) {
            names.add(Messages.text("keyName.altGraph"));
        }
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            names.add(Messages.text("keyName.shift"));
        }
        return names;
    }

    /** ⌃ ⌥ ⇧ ⌘, the order macOS writes them in. */
    private static List<String> macModifiers(int modifiers) {
        List<String> symbols = new ArrayList<>();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            symbols.add("⌃");
        }
        if ((modifiers & (InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK)) != 0) {
            symbols.add("⌥");
        }
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            symbols.add("⇧");
        }
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
            symbols.add("⌘");
        }
        return symbols;
    }

    /**
     * A key with a name on the keyboard gets it from the bundles; letters, digits and F-keys are themselves; a symbol
     * key is its symbol. Anything else falls back to Swing's name.
     */
    private static String keyName(int keyCode) {
        if ((keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z)
                || (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)) {
            return String.valueOf((char) keyCode);
        }
        if (keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12) {
            return "F" + (keyCode - KeyEvent.VK_F1 + 1);
        }
        if (keyCode >= KeyEvent.VK_F13 && keyCode <= KeyEvent.VK_F24) {
            return "F" + (keyCode - KeyEvent.VK_F13 + 13);
        }
        return switch (keyCode) {
            case KeyEvent.VK_DELETE -> Messages.text("keyName.delete");
            case KeyEvent.VK_BACK_SPACE -> Messages.text("keyName.backSpace");
            case KeyEvent.VK_SPACE -> Messages.text("keyName.space");
            case KeyEvent.VK_INSERT -> Messages.text("keyName.insert");
            case KeyEvent.VK_ENTER -> Messages.text("keyName.enter");
            case KeyEvent.VK_ESCAPE -> Messages.text("keyName.escape");
            case KeyEvent.VK_TAB -> Messages.text("keyName.tab");
            case KeyEvent.VK_HOME -> Messages.text("keyName.home");
            case KeyEvent.VK_END -> Messages.text("keyName.end");
            case KeyEvent.VK_PAGE_UP -> Messages.text("keyName.pageUp");
            case KeyEvent.VK_PAGE_DOWN -> Messages.text("keyName.pageDown");
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            default -> symbolOrSwingName(keyCode);
        };
    }

    private static String symbolOrSwingName(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_MINUS -> "-";
            case KeyEvent.VK_EQUALS -> "=";
            case KeyEvent.VK_PLUS -> "+";
            case KeyEvent.VK_COMMA -> ",";
            case KeyEvent.VK_PERIOD -> ".";
            case KeyEvent.VK_SLASH -> "/";
            case KeyEvent.VK_BACK_SLASH -> "\\";
            case KeyEvent.VK_SEMICOLON -> ";";
            case KeyEvent.VK_QUOTE -> "'";
            case KeyEvent.VK_BACK_QUOTE -> "`";
            case KeyEvent.VK_OPEN_BRACKET -> "[";
            case KeyEvent.VK_CLOSE_BRACKET -> "]";
            default -> KeyEvent.getKeyText(keyCode);
        };
    }
}
