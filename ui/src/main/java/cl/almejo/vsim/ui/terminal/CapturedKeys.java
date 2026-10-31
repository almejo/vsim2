package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.terminal.SpecialKey;
import cl.almejo.vsim.ui.app.OpenDocument;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The keys of a window while a part has its keyboard ({@link KeyboardCapture}, decision 5), given to the canvas key
 * listener before any binding runs. Printable characters, Enter, Retroceso, Tab, the arrows, Inicio, Fin, Supr and
 * (with «Flechas y teclas F como secuencias VT») the F keys go to the part, encoded with its own parameters
 * ({@link KeyEncoding#forParameters}); the paste shortcut types the clipboard text into it; Ctrl + a letter is a
 * control code only when the window has no shortcut on it, so Ctrl+S, Ctrl+Z and the rest keep working; a character
 * typed with AltGr (Ctrl+Alt on Windows) or a Mac's Option is text unless its stroke is bound. Esc ends the
 * capture. A plain key that sends nothing is still swallowed, so no canvas key (R, Supr, the arrows) acts on the
 * selection. Typing is never an edit of the document. EDT only.
 */
public final class CapturedKeys {

    /** The modifiers that make a key a command (a shortcut) rather than text. */
    private static final int COMMAND_MODIFIERS = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;
    /** The space: typed characters below it are the control keys, already handled on their press. */
    private static final char FIRST_PRINTABLE = ' ';
    private static final char DELETE_CHARACTER = 0x7F;

    private static final Map<Integer, SpecialKey> SPECIAL_KEYS = Map.ofEntries(
            Map.entry(KeyEvent.VK_ENTER, SpecialKey.ENTER), Map.entry(KeyEvent.VK_BACK_SPACE, SpecialKey.BACKSPACE),
            Map.entry(KeyEvent.VK_TAB, SpecialKey.TAB), Map.entry(KeyEvent.VK_DELETE, SpecialKey.DELETE),
            Map.entry(KeyEvent.VK_UP, SpecialKey.UP), Map.entry(KeyEvent.VK_DOWN, SpecialKey.DOWN),
            Map.entry(KeyEvent.VK_LEFT, SpecialKey.LEFT), Map.entry(KeyEvent.VK_RIGHT, SpecialKey.RIGHT),
            Map.entry(KeyEvent.VK_KP_UP, SpecialKey.UP), Map.entry(KeyEvent.VK_KP_DOWN, SpecialKey.DOWN),
            Map.entry(KeyEvent.VK_KP_LEFT, SpecialKey.LEFT), Map.entry(KeyEvent.VK_KP_RIGHT, SpecialKey.RIGHT),
            Map.entry(KeyEvent.VK_HOME, SpecialKey.HOME), Map.entry(KeyEvent.VK_END, SpecialKey.END),
            Map.entry(KeyEvent.VK_F1, SpecialKey.F1), Map.entry(KeyEvent.VK_F2, SpecialKey.F2),
            Map.entry(KeyEvent.VK_F3, SpecialKey.F3), Map.entry(KeyEvent.VK_F4, SpecialKey.F4),
            Map.entry(KeyEvent.VK_F5, SpecialKey.F5), Map.entry(KeyEvent.VK_F6, SpecialKey.F6),
            Map.entry(KeyEvent.VK_F7, SpecialKey.F7), Map.entry(KeyEvent.VK_F8, SpecialKey.F8),
            Map.entry(KeyEvent.VK_F9, SpecialKey.F9), Map.entry(KeyEvent.VK_F10, SpecialKey.F10),
            Map.entry(KeyEvent.VK_F11, SpecialKey.F11), Map.entry(KeyEvent.VK_F12, SpecialKey.F12));

    /** What the window binds, asked so that a captured key never takes an application shortcut away. */
    public interface Shortcuts {

        /** Whether {@code stroke} runs something of the window (a menu accelerator or a key binding) from {@code focused}. */
        boolean isBound(KeyStroke stroke, java.awt.Component focused);

        /** Whether {@code stroke} is the window's paste shortcut (Ctrl+V by default). */
        boolean isPaste(KeyStroke stroke);
    }

    private final KeyboardCapture capture;
    private final OpenDocument openDocument;
    private final Shortcuts shortcuts;
    private final Consumer<String> notices;
    private final Platform platform;
    private Supplier<Optional<String>> clipboardText = Optional::empty;
    /** The last press went to the application (a shortcut): the character it may type is not the part's. */
    private boolean pressLeftToTheApplication;
    /** The first half of a character outside the BMP, typed as two characters; 0 when none is pending. */
    private char pendingHighSurrogate;

    /**
     * Where VSim runs, for the modifiers that type characters: AltGr (Ctrl+Alt on Windows) everywhere, and Option
     * alone on a Mac, where Alt is not the menus' mnemonic key («Option+2» is «@»).
     */
    public enum Platform {
        PC, MAC;

        /** The platform of this Java. */
        public static Platform current() {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac") ? MAC : PC;
        }
    }

    /**
     * Keys for {@code capture} of a window on {@code openDocument} on this platform; {@code notices} shows a status-bar
     * notice (a paste that did not fit).
     */
    public CapturedKeys(KeyboardCapture capture, OpenDocument openDocument, Shortcuts shortcuts,
                        Consumer<String> notices) {
        this(capture, openDocument, shortcuts, notices, Platform.current());
    }

    /** Keys as on {@code platform} (tests try the Mac's Option on any system). */
    public CapturedKeys(KeyboardCapture capture, OpenDocument openDocument, Shortcuts shortcuts,
                        Consumer<String> notices, Platform platform) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.openDocument = Objects.requireNonNull(openDocument, "openDocument");
        this.shortcuts = Objects.requireNonNull(shortcuts, "shortcuts");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    /** Where a paste reads its text from (the system clipboard's plain text in the editor; a fake in tests). */
    public void useClipboard(Supplier<Optional<String>> clipboardText) {
        this.clipboardText = Objects.requireNonNull(clipboardText, "clipboardText");
    }

    /** A key pressed on a pane: true when it was the part's (or Esc), so the canvas consumes it and no binding runs. */
    public boolean keyPressed(KeyEvent event) {
        Optional<String> target = capture.captured();
        if (target.isEmpty()) {
            return false;
        }
        pressLeftToTheApplication = false;
        int keyCode = event.getKeyCode();
        if (isModifierKey(keyCode)) {
            return false; // Shift, Ctrl or Alt alone: the canvas keeps track of them
        }
        if (keyCode == KeyEvent.VK_ESCAPE && (event.getModifiersEx() & COMMAND_MODIFIERS) == 0) {
            if ((event.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
                return pressSpecial(target.get(), SpecialKey.ESCAPE); // Shift+Esc types the ESC byte
            }
            capture.release();
            return true;
        }
        KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(event);
        if (shortcuts.isPaste(stroke)) {
            paste(target.get());
            return true;
        }
        Optional<SpecialKey> special = Optional.ofNullable(SPECIAL_KEYS.get(keyCode));
        if (special.isPresent() && isArrow(special.get())) {
            pressSpecial(target.get(), special.get());
            return true; // with any modifier: an arrow never moves the selection while capturing
        }
        if ((event.getModifiersEx() & COMMAND_MODIFIERS) != 0) {
            return pressCommand(target.get(), event, stroke);
        }
        if (special.isPresent()) {
            return pressSpecial(target.get(), special.get());
        }
        return true; // a plain key: its character arrives as a typed key
    }

    /** A character typed on a pane: sent to the part (when printable); always consumed while capturing. */
    public boolean keyTyped(KeyEvent event) {
        Optional<String> target = capture.captured();
        if (target.isEmpty()) {
            return false;
        }
        char character = event.getKeyChar();
        if (pressLeftToTheApplication || !isText(event, character)) {
            return true;
        }
        if (Character.isHighSurrogate(character)) {
            pendingHighSurrogate = character;
            return true;
        }
        int codePoint = character;
        if (Character.isLowSurrogate(character)) {
            if (pendingHighSurrogate == 0) {
                return true;
            }
            codePoint = Character.toCodePoint(pendingHighSurrogate, character);
        }
        pendingHighSurrogate = 0;
        send(target.get(), encodingOf(target.get()).encode(codePoint));
        return true;
    }

    /**
     * Ctrl, Alt, AltGr or ⌘ with a key: the application's when bound; else Ctrl + a letter is a control code, and
     * AltGr (Ctrl+Alt) or a Mac's Option leave the character they type to arrive as a typed key («@» is AltGr+Q on a
     * Latin American layout).
     */
    private boolean pressCommand(String target, KeyEvent event, KeyStroke stroke) {
        if (shortcuts.isBound(stroke, event.getComponent())) {
            pressLeftToTheApplication = true;
            return false;
        }
        int commandModifiers = event.getModifiersEx() & COMMAND_MODIFIERS;
        int keyCode = event.getKeyCode();
        if (commandModifiers == InputEvent.CTRL_DOWN_MASK && keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
            send(target, encodingOf(target).encodeControl((char) keyCode));
            return true;
        }
        if (typesCharacters(commandModifiers)) {
            return true;
        }
        pressLeftToTheApplication = true; // Alt + a letter opens a menu, as without capture
        return false;
    }

    /** Whether {@code commandModifiers} make a key type a character: AltGr, Ctrl+Alt, or Option alone on a Mac. */
    private boolean typesCharacters(int commandModifiers) {
        if ((commandModifiers & InputEvent.META_DOWN_MASK) != 0) {
            return false;
        }
        int controlAndAlt = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        return (commandModifiers & InputEvent.ALT_GRAPH_DOWN_MASK) != 0
                || (commandModifiers & controlAndAlt) == controlAndAlt
                || platform == Platform.MAC && commandModifiers == InputEvent.ALT_DOWN_MASK;
    }

    /**
     * Enter, Retroceso, Tab, an arrow, Inicio, Fin, Supr or an F key: its bytes, or else its edit of the queue. An F key
     * that sends nothing is left to the application (F5 still starts the simulation).
     */
    private boolean pressSpecial(String target, SpecialKey key) {
        Optional<byte[]> bytes = encodingOf(target).encode(key);
        if (bytes.isPresent()) {
            send(target, bytes.get());
            return true;
        }
        if (key.typingEdit().isPresent()) {
            openDocument.editTyping(target, key.typingEdit().get());
            return true;
        }
        if (isArrow(key)) {
            return true;
        }
        pressLeftToTheApplication = true;
        return false;
    }

    /** Types the clipboard text into {@code target}; when it does not all fit, the status bar says how much went in. */
    private void paste(String target) {
        KeyboardPaste.paste(openDocument, target, clipboardText.get().orElse(""), notices);
    }

    private void send(String target, byte[] bytes) {
        if (bytes.length > 0) {
            openDocument.typeBytes(target, bytes);
        }
    }

    /** The encoding the parameters of {@code componentId} ask for; the Teclado's defaults when it is gone. */
    private KeyEncoding encodingOf(String componentId) {
        return KeyboardPaste.encodingOf(openDocument, componentId);
    }

    /**
     * A character the user typed as text: printable, typed without modifiers or with the ones that type characters
     * (AltGr, Ctrl+Alt, a Mac's Option); not the result of Ctrl or ⌘.
     */
    private boolean isText(KeyEvent event, char character) {
        if (character == KeyEvent.CHAR_UNDEFINED || character < FIRST_PRINTABLE || character == DELETE_CHARACTER) {
            return false;
        }
        int commandModifiers = event.getModifiersEx() & COMMAND_MODIFIERS;
        return commandModifiers == 0 || typesCharacters(commandModifiers);
    }

    private static boolean isArrow(SpecialKey key) {
        return key == SpecialKey.UP || key == SpecialKey.DOWN || key == SpecialKey.LEFT || key == SpecialKey.RIGHT;
    }

    private static boolean isModifierKey(int keyCode) {
        return keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_ALT
                || keyCode == KeyEvent.VK_META || keyCode == KeyEvent.VK_ALT_GRAPH;
    }
}
