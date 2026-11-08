package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.terminal.TypingEdit;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.tools.TestToolContext;
import cl.almejo.vsim.ui.tools.WireMode;
import cl.almejo.vsim.ui.tools.WireTool;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.panes.CanvasPanes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keyboard capture (decision 5): a click on a part that takes typing captures the keyboard; its keys reach the part
 * through the simulation thread and never the canvas bindings, while the application's own shortcuts keep working;
 * the capture ends with Esc, focus loss, a tool change, a click elsewhere or the part going away.
 */
class KeyboardCaptureTest {

    private static final String TYPIST = "test-typist";
    private static final int TYPIST_X = 10;
    private static final int TYPIST_Y = 10;
    /** A point inside the typist's 4 × 4 body. */
    private static final double INSIDE_X = TYPIST_X + 2;
    private static final double INSIDE_Y = TYPIST_Y + 2;

    private final List<Byte> received = new CopyOnWriteArrayList<>();
    private final List<TypingEdit> edits = new CopyOnWriteArrayList<>();
    private EditorFixture fixture;
    private OpenDocument openDocument;
    private EditorView view;
    private Component typist;

    /** A part that takes up to five bytes of typing (a tiny Teclado), with the Teclado's encoding parameters. */
    private final class Typist implements Behavior {

        private static final int CAPACITY = 5;

        @Override
        public void inputChanged(BehaviorContext context, int pinIndex) {
        }

        @Override
        public boolean acceptsTyping() {
            return true;
        }

        @Override
        public int acceptBytes(BehaviorContext context, byte[] bytes) {
            if (received.size() + bytes.length > CAPACITY) {
                return 0;
            }
            for (byte value : bytes) {
                received.add(value);
            }
            return bytes.length;
        }

        @Override
        public void editTyping(BehaviorContext context, TypingEdit edit) {
            edits.add(edit);
        }
    }

    private ComponentTypeRegistry registryWithATypist() {
        ComponentTypeRegistry registry = StandardComponentTypes.registry().copy();
        registry.register(new ComponentType(TYPIST, ComponentCategory.INPUT_OUTPUT, new GridSize(4, 4),
                List.of(PinSpec.output("D", 4, 2, KeyEncoding.EIGHT_BITS)),
                List.of(ParameterSpec.choice(KeyEncoding.ENTER_SENDS, KeyEncoding.LF_VALUE,
                                List.of(KeyEncoding.LF_VALUE, KeyEncoding.CR_VALUE, KeyEncoding.CR_LF_VALUE)),
                        ParameterSpec.bool(KeyEncoding.ARROWS_AS_SEQUENCES, false)),
                parameters -> new Typist()));
        return registry;
    }

    @BeforeEach
    void create() {
        fixture = EditorFixture.around(OpenDocument.createEmpty(registryWithATypist()));
        openDocument = fixture.openDocument();
        view = fixture.view();
        typist = UiTestSupport.place(openDocument, TYPIST, TYPIST_X, TYPIST_Y, Map.of());
        UiTestSupport.place(openDocument, StandardComponentTypes.SWITCH, 30, 30, Map.of());
        awaitSimulation(openDocument);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private KeyboardCapture capture() {
        return view.keyboardCapture();
    }

    private void click(CircuitCanvas canvas, double gridX, double gridY) {
        canvas.tool().mousePressed(canvas, TestToolContext.at(gridX, gridY));
        canvas.tool().mouseReleased(canvas, TestToolContext.at(gridX, gridY));
    }

    private void clickTheTypist() {
        click(view.canvas(), INSIDE_X, INSIDE_Y);
    }

    /** What Swing does with a key pressed on {@code canvas}: its key listeners, then (if not consumed) its bindings. */
    private static KeyEvent press(CircuitCanvas canvas, int keyCode, int modifiers, char character) {
        KeyEvent event = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, character);
        for (KeyListener listener : canvas.getKeyListeners()) {
            listener.keyPressed(event);
        }
        if (!event.isConsumed()) {
            SwingUtilities.processKeyBindings(event);
        }
        return event;
    }

    private static KeyEvent type(CircuitCanvas canvas, char character, int modifiers) {
        KeyEvent event = new KeyEvent(canvas, KeyEvent.KEY_TYPED, 0, modifiers, KeyEvent.VK_UNDEFINED, character);
        for (KeyListener listener : canvas.getKeyListeners()) {
            listener.keyTyped(event);
        }
        return event;
    }

    /** A printable key as the platform sends it: a press and then the typed character. */
    private static void typeKey(CircuitCanvas canvas, int keyCode, char character) {
        press(canvas, keyCode, 0, character);
        type(canvas, character, 0);
    }

    private static void loseFocus(CircuitCanvas canvas, java.awt.Component opposite) {
        FocusEvent event = new FocusEvent(canvas, FocusEvent.FOCUS_LOST, false, opposite);
        for (FocusListener listener : canvas.getFocusListeners()) {
            listener.focusLost(event);
        }
    }

    private List<Byte> receivedAfterTheSimulation() {
        awaitSimulation(openDocument);
        return new ArrayList<>(received);
    }

    private static List<Byte> bytesOf(String text) {
        List<Byte> bytes = new ArrayList<>();
        for (char character : text.toCharArray()) {
            bytes.add((byte) character);
        }
        return bytes;
    }

    @Test
    void aClickOnAPartThatTakesTypingSelectsItAndCapturesTheKeyboard() {
        clickTheTypist();

        assertThat(capture().captured()).contains(typist.id().value());
        assertThat(view.selection().ids()).containsExactly(typist.id());
    }

    @Test
    void draggingThePartMovesItWithoutCapturing() {
        CircuitCanvas canvas = view.canvas();
        canvas.tool().mousePressed(canvas, TestToolContext.at(INSIDE_X, INSIDE_Y));
        canvas.tool().mouseDragged(canvas, TestToolContext.at(INSIDE_X + 3, INSIDE_Y));
        canvas.tool().mouseReleased(canvas, TestToolContext.at(INSIDE_X + 3, INSIDE_Y));

        assertThat(openDocument.document().component(typist.id()).orElseThrow().position())
                .isNotEqualTo(new GridPoint(TYPIST_X, TYPIST_Y));
        assertThat(capture().captured()).isEmpty();
    }

    @Test
    void aClickOnAPartThatDoesNotTakeTypingCapturesNothing() {
        click(view.canvas(), 31, 31);

        assertThat(capture().captured()).isEmpty();
    }

    @Test
    void typedCharactersReachThePartAndAreConsumed() {
        clickTheTypist();

        KeyEvent pressed = press(view.canvas(), KeyEvent.VK_H, 0, 'h');
        KeyEvent typed = type(view.canvas(), 'h', 0);
        typeKey(view.canvas(), KeyEvent.VK_I, 'i');

        assertThat(pressed.isConsumed()).isTrue();
        assertThat(typed.isConsumed()).isTrue();
        assertThat(receivedAfterTheSimulation()).isEqualTo(bytesOf("hi"));
    }

    @Test
    void aPlainLetterThatIsACanvasKeyIsTypedAndDoesNotRotate() {
        clickTheTypist();

        typeKey(view.canvas(), KeyEvent.VK_R, 'r');

        assertThat(openDocument.document().component(typist.id()).orElseThrow().rotation())
                .isEqualTo(typist.rotation());
        assertThat(receivedAfterTheSimulation()).isEqualTo(bytesOf("r"));
    }

    @Test
    void enterSendsWhatThePartSays() {
        clickTheTypist();

        press(view.canvas(), KeyEvent.VK_ENTER, 0, '\n');
        type(view.canvas(), '\n', 0);

        assertThat(receivedAfterTheSimulation()).containsExactly((byte) '\n');
    }

    @Test
    void arrowsEditTheQueueAndNeverMoveTheSelection() {
        clickTheTypist();

        KeyEvent left = press(view.canvas(), KeyEvent.VK_LEFT, 0, KeyEvent.CHAR_UNDEFINED);
        KeyEvent up = press(view.canvas(), KeyEvent.VK_UP, 0, KeyEvent.CHAR_UNDEFINED);
        awaitSimulation(openDocument);

        assertThat(left.isConsumed()).isTrue();
        assertThat(up.isConsumed()).isTrue();
        assertThat(openDocument.document().component(typist.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(TYPIST_X, TYPIST_Y));
        assertThat(edits).containsExactly(TypingEdit.CURSOR_LEFT);
    }

    @Test
    void escapeEndsTheCapture() {
        clickTheTypist();

        KeyEvent escape = press(view.canvas(), KeyEvent.VK_ESCAPE, 0, KeyEvent.CHAR_UNDEFINED);

        assertThat(escape.isConsumed()).isTrue();
        assertThat(capture().captured()).isEmpty();
    }

    @Test
    void shiftEscapeSendsTheEscapeByteAndKeepsTheCapture() {
        clickTheTypist();

        KeyEvent escape = press(view.canvas(), KeyEvent.VK_ESCAPE, InputEvent.SHIFT_DOWN_MASK, KeyEvent.CHAR_UNDEFINED);

        assertThat(escape.isConsumed()).isTrue();
        assertThat(capture().captured()).contains(typist.id().value());
        assertThat(receivedAfterTheSimulation()).containsExactly((byte) 0x1B);
    }

    @Test
    void aBoundShortcutIsLeftToTheApplication() {
        clickTheTypist();

        KeyEvent save = press(view.canvas(), KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK, (char) 19);
        type(view.canvas(), (char) 19, InputEvent.CTRL_DOWN_MASK);

        assertThat(save.isConsumed()).isFalse();
        assertThat(receivedAfterTheSimulation()).isEmpty();
        assertThat(capture().captured()).isPresent();
    }

    @Test
    void anUnboundControlLetterIsTypedAsAControlCode() {
        clickTheTypist();

        KeyEvent controlL = press(view.canvas(), KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK, (char) 12);
        type(view.canvas(), (char) 12, InputEvent.CTRL_DOWN_MASK);

        assertThat(controlL.isConsumed()).isTrue();
        assertThat(receivedAfterTheSimulation()).containsExactly((byte) 12);
    }

    @Test
    void pasteSendsTheClipboardTextIntoThePartAndNotOntoTheCanvas() {
        view.capturedKeys().useClipboard(() -> Optional.of("ab\r\nc"));
        int elements = openDocument.document().components().size();
        clickTheTypist();

        KeyEvent paste = press(view.canvas(), KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK, (char) 22);

        assertThat(paste.isConsumed()).isTrue();
        assertThat(receivedAfterTheSimulation()).isEqualTo(bytesOf("ab\nc"));
        assertThat(openDocument.document().components()).hasSize(elements);
    }

    @Test
    void aPasteThatDoesNotFitSaysHowMuchWentIn() {
        view.capturedKeys().useClipboard(() -> Optional.of("abcdefgh"));
        clickTheTypist();

        press(view.canvas(), KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK, (char) 22);
        awaitSimulation(openDocument);
        UiTestSupport.waitUntil(() -> view.statusBar().text().contains("8"));

        assertThat(receivedAfterTheSimulation()).isEqualTo(bytesOf("abcde"));
        assertThat(view.statusBar().text()).contains("5").contains("8");
    }

    @Test
    void typingIsNotAnEdit() {
        boolean modifiedBefore = openDocument.isModified();
        Optional<String> undoBefore = openDocument.history().undoDescription();
        clickTheTypist();

        typeKey(view.canvas(), KeyEvent.VK_A, 'a');
        awaitSimulation(openDocument);

        assertThat(openDocument.history().undoDescription()).isEqualTo(undoBefore);
        assertThat(openDocument.isModified()).isEqualTo(modifiedBefore);
    }

    @Test
    void losingTheFocusToAnotherWindowOrComponentEndsTheCapture() {
        clickTheTypist();

        loseFocus(view.canvas(), null);

        assertThat(capture().captured()).isEmpty();
    }

    @Test
    void changingTheToolEndsTheCapture() {
        clickTheTypist();

        view.canvas().setTool(new WireTool(() -> WireMode.PIECES));

        assertThat(capture().captured()).isEmpty();
    }

    @Test
    void aClickElsewhereEndsTheCapture() {
        clickTheTypist();

        click(view.canvas(), 50, 50);

        assertThat(capture().captured()).isEmpty();
    }

    @Test
    void deletingThePartEndsTheCaptureAndUndoingDoesNotBringItBack() {
        clickTheTypist();

        UiTestSupport.perform(view.actions().delete());
        assertThat(capture().captured()).isEmpty();
        openDocument.history().undo();

        assertThat(openDocument.document().component(typist.id())).isPresent();
        assertThat(capture().captured()).isEmpty();
    }

    @Test
    void restartingTheSimulationKeepsTheCapture() {
        clickTheTypist();

        openDocument.restart();
        awaitSimulation(openDocument);

        assertThat(capture().captured()).contains(typist.id().value());
    }

    @Test
    void thePanesOfOneWindowShareTheCapture() {
        view.canvasPanes().split(CanvasPanes.Orientation.HORIZONTAL, 0.5);
        CircuitCanvas first = view.panes().get(0);
        CircuitCanvas second = view.panes().get(1);
        click(first, INSIDE_X, INSIDE_Y);

        loseFocus(first, second);
        typeKey(second, KeyEvent.VK_X, 'x');

        assertThat(capture().captured()).contains(typist.id().value());
        assertThat(receivedAfterTheSimulation()).isEqualTo(bytesOf("x"));
    }

    @Test
    void tabStaysOnTheCanvasWhileCapturing() {
        clickTheTypist();
        assertThat(view.canvas().getFocusTraversalKeysEnabled()).isFalse();

        press(view.canvas(), KeyEvent.VK_ESCAPE, 0, KeyEvent.CHAR_UNDEFINED);

        assertThat(view.canvas().getFocusTraversalKeysEnabled()).isTrue();
    }

    @Test
    void anotherWindowOnTheSameDocumentHasItsOwnCapture() {
        EditorView other = fixture.otherView();
        clickTheTypist();

        assertThat(other.keyboardCapture().captured()).isEmpty();
        typeKey(other.canvas(), KeyEvent.VK_Q, 'q');
        assertThat(receivedAfterTheSimulation()).isEmpty();
    }

    @Test
    void theCaptureTellsItsListeners() {
        List<Optional<String>> seen = new ArrayList<>();
        capture().addListener(() -> seen.add(capture().captured()));

        clickTheTypist();
        capture().release();

        assertThat(seen).containsExactly(Optional.of(typist.id().value()), Optional.empty());
    }

    @Test
    void aCapturedIdIsAPlainComponentId() {
        clickTheTypist();

        assertThat(openDocument.document().component(new ElementId(capture().captured().orElseThrow()))).isPresent();
    }

    @Test
    void altGraphAndControlAltCharactersAreTyped() {
        clickTheTypist();
        // AltGr+Q on a Latin American layout gives @; on Windows AltGr arrives as Ctrl+Alt
        press(view.canvas(), KeyEvent.VK_Q, InputEvent.ALT_GRAPH_DOWN_MASK, '@');
        type(view.canvas(), '@', InputEvent.ALT_GRAPH_DOWN_MASK);
        press(view.canvas(), KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK, '@');
        type(view.canvas(), '@', InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
        // AltGr+Shift+7 on a Spanish layout gives a backslash in some systems
        press(view.canvas(), KeyEvent.VK_7, InputEvent.ALT_GRAPH_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, '\\');
        type(view.canvas(), '\\', InputEvent.ALT_GRAPH_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);

        assertThat(receivedAfterTheSimulation()).isEqualTo(bytesOf("@@\\"));
    }

    /** Keys handled by a {@link CapturedKeys} of {@code platform} on the typist, with {@code bound} as the shortcuts. */
    private CapturedKeys keysOf(CapturedKeys.Platform platform, KeyStroke bound) {
        KeyboardCapture standalone = new KeyboardCapture(openDocument.document());
        standalone.capture(typist.id().value());
        CapturedKeys.Shortcuts shortcuts = new CapturedKeys.Shortcuts() {
            @Override
            public boolean isBound(KeyStroke stroke, java.awt.Component focused) {
                return stroke.equals(bound);
            }

            @Override
            public boolean isPaste(KeyStroke stroke) {
                return false;
            }
        };
        return new CapturedKeys(standalone, openDocument, shortcuts, notice -> { }, platform);
    }

    private void typeWith(CapturedKeys keys, int keyCode, int modifiers, char character) {
        keys.keyPressed(new KeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, character));
        keys.keyTyped(new KeyEvent(view.canvas(), KeyEvent.KEY_TYPED, 0, modifiers, KeyEvent.VK_UNDEFINED, character));
    }

    @Test
    void optionCharactersAreTypedOnAMac() {
        CapturedKeys keys = keysOf(CapturedKeys.Platform.MAC, null);

        typeWith(keys, KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK, '@');
        typeWith(keys, KeyEvent.VK_7, InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, '\\');

        assertThat(receivedAfterTheSimulation()).isEqualTo(bytesOf("@\\"));
    }

    @Test
    void altWithALetterIsStillTheMenusOnAPcAndABoundStrokeIsStillTheApplications() {
        CapturedKeys pc = keysOf(CapturedKeys.Platform.PC,
                KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));

        typeWith(pc, KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK, 'a');
        typeWith(pc, KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK, '@');

        assertThat(receivedAfterTheSimulation()).isEmpty();
    }

    /** A pins-mode UART at (50, 10) and an integrated one at (50, 80), clicked just inside their corner. */
    private Component[] twoUarts() {
        Component pins = UiTestSupport.place(openDocument, StandardComponentTypes.UART_16550, 50, 10,
                Map.of(UartTypes.SCREEN, UartTypes.SCREEN_PINS));
        Component integrated = UiTestSupport.place(openDocument, StandardComponentTypes.UART_16550, 50, 80,
                Map.of(UartTypes.SCREEN, UartTypes.SCREEN_INTEGRATED));
        awaitSimulation(openDocument);
        return new Component[]{pins, integrated};
    }

    @Test
    void anIntegratedUartCapturesAfterAPinsUartWasClicked() {
        Component[] uarts = twoUarts();

        click(view.canvas(), 51.5, 11.5);
        assertThat(capture().captured()).as("a UART with TX and RX pins never captures").isEmpty();
        click(view.canvas(), 51.5, 81.5);

        assertThat(capture().captured()).contains(uarts[1].id().value());
    }

    @Test
    void aPinsUartNeverCapturesAfterAnIntegratedUartWasClicked() {
        Component[] uarts = twoUarts();

        click(view.canvas(), 51.5, 81.5);
        assertThat(capture().captured()).contains(uarts[1].id().value());
        capture().release();
        click(view.canvas(), 51.5, 11.5);

        assertThat(capture().captured()).isEmpty();
    }
}
