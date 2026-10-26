package cl.almejo.vsim.ui.inline;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.paint.TextFonts;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class InlineTextEditorTest {

    private static final Rectangle2D AREA = new Rectangle2D.Double(80, 40, 160, 24);
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Function<String, Optional<String>> NOT_BAD =
            text -> text.equals("bad") ? Optional.of("Mal") : Optional.empty();

    private OpenDocument openDocument;
    private EditorFixture fixture;
    private EditorView view;
    private CircuitCanvas canvas;
    private InlineTextEditor editor;
    private final List<String> confirmed = new ArrayList<>();

    @BeforeEach
    void create() {
        fixture = EditorFixture.create();
        openDocument = fixture.openDocument();
        view = fixture.view();
        canvas = view.canvas();
        canvas.setSize(800, 600);
        editor = new InlineTextEditor(() -> canvas);
    }

    @AfterEach
    void close() {
        editor.dispose();
        fixture.close();
    }

    private InlineEditRequest request(String text) {
        return request(text, InlineValidators.anyText());
    }

    private InlineEditRequest request(String text, Function<String, Optional<String>> validator) {
        return new InlineEditRequest(AREA, FONT, text, validator, confirmed::add);
    }

    private static KeyEvent key(JComponent target, int keyCode, int modifiers) {
        return new KeyEvent(target, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private void type(int keyCode) {
        deliver(key(editor.field(), keyCode, 0));
    }

    /** What the focus manager does with a key typed while the field has the focus. */
    private void deliver(java.awt.AWTEvent event) {
        if (event instanceof KeyEvent keyEvent) {
            ((InlineTextEditor.Field) editor.field()).deliver(keyEvent);
        } else {
            editor.field().dispatchEvent(event);
        }
    }

    private void pressOn(java.awt.Component target, int x, int y) {
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, x, y,
                1, false, MouseEvent.BUTTON1));
    }

    /** The field's text area (its bounds minus its insets) in canvas pixels. */
    private Rectangle textArea() {
        Rectangle bounds = SwingUtilities.convertRectangle(editor.field().getParent(), editor.field().getBounds(),
                canvas);
        Insets insets = editor.field().getInsets();
        return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
                bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
    }

    @Test
    void openingShowsTheTextAllSelectedOverTheCanvasWithTheirTooltips() {
        editor.open(request("hola"));

        assertThat(editor.isOpen()).isTrue();
        assertThat(editor.field().getText()).isEqualTo("hola");
        assertThat(editor.field().getSelectedText()).isEqualTo("hola");
        assertThat(SwingUtilities.isDescendingFrom(editor.field(), canvas)).isTrue();
        assertThat(editor.confirmButton().getToolTipText()).isEqualTo("Confirmar (Enter)");
        assertThat(editor.cancelButton().getToolTipText()).isEqualTo("Cancelar (Esc)");
        assertThat(editor.confirmButton().getIcon()).isNotNull();
        assertThat(editor.cancelButton().getIcon()).isNotNull();
        assertThat(editor.problemText().isVisible()).isFalse();
    }

    @Test
    void enterAndTheConfirmButtonConfirmTheText() {
        editor.open(request("uno"));
        editor.field().setText("dos");
        type(KeyEvent.VK_ENTER);
        assertThat(confirmed).containsExactly("dos");
        assertThat(editor.isOpen()).isFalse();
        assertThat(SwingUtilities.isDescendingFrom(editor.field(), canvas)).isFalse();

        editor.open(request("tres"));
        editor.confirmButton().doClick();
        assertThat(confirmed).containsExactly("dos", "tres");
        assertThat(editor.isOpen()).isFalse();
    }

    @Test
    void escapeAndTheCancelButtonChangeNothing() {
        editor.open(request("uno"));
        editor.field().setText("dos");
        type(KeyEvent.VK_ESCAPE);
        assertThat(editor.isOpen()).isFalse();

        editor.open(request("tres"));
        editor.cancelButton().doClick();
        assertThat(editor.isOpen()).isFalse();
        assertThat(confirmed).isEmpty();
    }

    @Test
    void aClickOutsideOnTheCanvasConfirms() {
        editor.open(request("uno"));
        editor.field().setText("dos");
        pressOn(canvas, 600, 500);
        assertThat(confirmed).containsExactly("dos");
        assertThat(editor.isOpen()).isFalse();
    }

    @Test
    void aClickElsewhereInTheSameWindowConfirmsButAClickInsideTheEditorDoesNot() {
        editor.open(request("uno"));
        pressOn(editor.field(), 2, 2);
        pressOn(editor.confirmButton().getParent(), 1, 1);
        assertThat(editor.isOpen()).isTrue();

        pressOn(view.toolBar(), 3, 3);
        assertThat(confirmed).containsExactly("uno");
    }

    @Test
    void focusLostToAnotherWindowKeepsTheEditorOpenWithItsText() {
        editor.open(request("uno"));
        editor.field().setText("a medias");

        deliver(new FocusEvent(editor.field(), FocusEvent.FOCUS_LOST, false, null));
        pressOn(new JPanel(), 1, 1); // a press in another window
        assertThat(editor.isOpen()).isTrue();
        assertThat(editor.field().getText()).isEqualTo("a medias");
        assertThat(confirmed).isEmpty();
    }

    @Test
    void aProblemShowsBelowTheFieldAndBlocksEnterAndConfirmWhileAClickOutsideCancels() {
        editor.open(request("bien", NOT_BAD));
        assertThat(editor.confirmButton().isEnabled()).isTrue();

        editor.field().setText("bad");
        assertThat(editor.problemText().isVisible()).isTrue();
        assertThat(editor.problemText().getText()).isEqualTo("Mal");
        assertThat(editor.field().getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(editor.problemText().getY()).isGreaterThanOrEqualTo(
                editor.field().getY() + editor.field().getHeight());
        assertThat(editor.confirmButton().isEnabled()).isFalse();

        type(KeyEvent.VK_ENTER);
        editor.confirmButton().doClick();
        editor.confirm();
        assertThat(editor.isOpen()).isTrue();
        assertThat(confirmed).isEmpty();

        pressOn(canvas, 600, 500);
        assertThat(editor.isOpen()).isFalse();
        assertThat(confirmed).isEmpty();
    }

    @Test
    void fixingTheProblemClearsItAndConfirmWorksAgain() {
        editor.open(request("bad", NOT_BAD));
        assertThat(editor.problemText().isVisible()).as("the initial text is checked too").isTrue();
        editor.field().setText("good");
        assertThat(editor.problemText().isVisible()).isFalse();
        assertThat(editor.field().getClientProperty("JComponent.outline")).isNull();
        editor.confirm();
        assertThat(confirmed).containsExactly("good");
    }

    @Test
    void openingASecondRequestConfirmsTheFirst() {
        editor.open(new InlineEditRequest(AREA, FONT, "primero", InlineValidators.anyText(),
                text -> confirmed.add("1:" + text)));
        editor.open(new InlineEditRequest(AREA, FONT, "segundo", InlineValidators.anyText(),
                text -> confirmed.add("2:" + text)));
        assertThat(confirmed).containsExactly("1:primero");
        assertThat(editor.isOpen()).isTrue();
        assertThat(editor.field().getText()).isEqualTo("segundo");
        assertThat(canvas.getComponentCount()).as("one editor at a time").isEqualTo(1);
    }

    @Test
    void zoomingFrom100To200PercentMovesAndDoublesTheField() {
        editor.open(request("hola"));
        assertThat(textArea()).isEqualTo(new Rectangle(80, 40, 160, 24));
        float fontAt100 = editor.field().getFont().getSize2D();

        canvas.viewport().zoomAt(0, 0, 2.0);

        assertThat(textArea()).isEqualTo(new Rectangle(160, 80, 320, 48));
        assertThat(editor.field().getFont().getSize2D()).isEqualTo(fontAt100 * 2);
        assertThat(editor.field().getFont().getFamily()).isEqualTo(FONT.getFamily());
    }

    @Test
    void scrollingMovesTheField() {
        editor.open(request("hola"));
        canvas.viewport().panBy(30, -10);
        assertThat(textArea()).isEqualTo(new Rectangle(110, 30, 160, 24));
    }

    @Test
    void atSmallZoomsTheTextStaysReadable() {
        canvas.viewport().zoomAt(0, 0, 0.25);
        editor.open(request("hola"));
        assertThat(editor.field().getFont().getSize2D()).isGreaterThanOrEqualTo(InlineTextEditor.MINIMUM_FONT_SIZE);
    }

    @Test
    void theFieldGrowsWithTheTextUpToAMaximum() {
        editor.open(request("a"));
        int shortWidth = editor.field().getWidth();
        editor.field().setText("a".repeat(60));
        int longerWidth = editor.field().getWidth();
        assertThat(longerWidth).isGreaterThan(shortWidth);
        editor.field().setText("a".repeat(2000));
        assertThat(editor.field().getWidth()).isLessThanOrEqualTo(InlineTextEditor.MAXIMUM_WIDTH);
        assertThat(editor.confirmButton().getX()).isGreaterThanOrEqualTo(
                editor.field().getX() + editor.field().getWidth());
    }

    @Test
    void keysTypedInTheFieldNeverReachTheCanvasOrTheWindowShortcuts() {
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        canvas.selection().select(led.id());
        double zoom = canvas.viewport().zoom();
        List<String> reached = new ArrayList<>();
        int menuMask = ShortcutCatalog.menuMask();
        KeyStroke undo = KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask);
        KeyStroke delete = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
        KeyStroke left = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0);
        for (KeyStroke stroke : List.of(undo, delete, left)) {
            view.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, stroke.toString());
            view.getActionMap().put(stroke.toString(), new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    reached.add(stroke.toString());
                }
            });
        }
        editor.open(request("hola"));

        KeyEvent deleteEvent = key(editor.field(), KeyEvent.VK_DELETE, 0);
        deliver(deleteEvent);
        assertThat(deleteEvent.isConsumed()).isTrue();
        assertThat(editor.field().getText()).as("Supr deletes the selected characters").isEmpty();

        editor.field().setText("abc");
        KeyEvent leftEvent = key(editor.field(), KeyEvent.VK_LEFT, 0);
        deliver(leftEvent);
        assertThat(leftEvent.isConsumed()).isTrue();
        // Headless the caret cannot be measured, so check the arrow is the field's own caret binding.
        assertThat(editor.field().getInputMap().get(left)).as("the arrow moves the caret").isNotNull();

        KeyEvent undoEvent = key(editor.field(), KeyEvent.VK_Z, menuMask);
        deliver(undoEvent);
        assertThat(undoEvent.isConsumed()).as("consumed keys never reach the menu accelerators").isTrue();

        KeyEvent zoomEvent = key(editor.field(), KeyEvent.VK_PLUS, menuMask);
        deliver(zoomEvent);
        assertThat(zoomEvent.isConsumed()).isTrue();

        assertThat(reached).isEmpty();
        assertThat(canvas.viewport().zoom()).isEqualTo(zoom);
        assertThat(openDocument.document().elements()).contains(led);
        assertThat(openDocument.history().canUndo()).isTrue();
        assertThat(canvas.selection().ids()).containsExactly(led.id());
        assertThat(editor.isOpen()).isTrue();
    }

    @Test
    void altKeysStillReachTheMenusForTheirMnemonics() {
        editor.open(request("hola"));
        KeyEvent mnemonic = key(editor.field(), KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK);
        deliver(mnemonic);
        assertThat(mnemonic.isConsumed()).isFalse();
    }

    @Test
    void theEditorClosesWithoutApplyingWhenWhatItEditsIsGone() {
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        editor.open(request("hola"), () -> openDocument.document().element(led.id()).isPresent());
        editor.field().setText("cambiado");

        openDocument.history().undo();

        assertThat(editor.isOpen()).isFalse();
        assertThat(confirmed).isEmpty();
    }

    @Test
    void anOpenEditorKeepsWorkingWhileWhatItEditsStays() {
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        editor.open(request("hola"), () -> openDocument.document().element(led.id()).isPresent());
        place(openDocument, StandardComponentTypes.LED, 30, 10, Map.of());
        assertThat(editor.isOpen()).isTrue();
    }

    @Test
    void disposeClosesWithoutApplyingAndStopsListening() {
        editor.open(request("hola"));
        editor.dispose();
        assertThat(editor.isOpen()).isFalse();
        pressOn(canvas, 600, 500);
        assertThat(confirmed).isEmpty();
        assertThat(canvas.getComponentCount()).isZero();
    }

    @Test
    void aPressOnTheProblemTextOrAnEmptyCornerNeverReachesTheCanvasNorCountsAsOutside() {
        editor.open(request("bien", NOT_BAD));
        editor.field().setText("bad");
        // a headless canvas does not route presses to its children, so check what the routing looks at: the
        // nearest ancestor of the problem text with a mouse listener is the editor's panel, not the canvas
        Rectangle problemBounds = SwingUtilities.convertRectangle(editor.problemText().getParent(),
                editor.problemText().getBounds(), canvas);
        pressOn(editor.problemText().getParent(), problemBounds.x, problemBounds.y);
        assertThat(editor.isOpen()).isTrue();
        assertThat(editor.problemText().getParent().getMouseListeners()).isNotEmpty();
    }

    @Test
    void theReusedComponentsFollowTheThemeWhileTheEditorIsClosed() {
        editor.open(request("uno"));
        editor.cancel();
        javax.swing.plaf.basic.BasicTextFieldUI stale = new javax.swing.plaf.basic.BasicTextFieldUI() { };
        editor.field().setUI(stale);
        editor.open(request("dos"));
        assertThat(editor.field().getUI()).isNotSameAs(stale);
    }

    @Test
    void tabAndShiftTabDoNothingSoTheFocusStaysInTheField() {
        assertThat(editor.field().getFocusTraversalKeysEnabled()).isFalse();
    }

    @Test
    void theCursorsAreHandOnTheButtonsTextOnTheFieldAndDefaultOnTheRest() {
        editor.open(request("uno", NOT_BAD));
        editor.field().setText("bad");
        assertThat(editor.confirmButton().getCursor().getType()).isEqualTo(java.awt.Cursor.HAND_CURSOR);
        assertThat(editor.cancelButton().getCursor().getType()).isEqualTo(java.awt.Cursor.HAND_CURSOR);
        assertThat(editor.field().getCursor().getType()).isEqualTo(java.awt.Cursor.TEXT_CURSOR);
        assertThat(editor.problemText().getCursor().getType()).isEqualTo(java.awt.Cursor.DEFAULT_CURSOR);
        assertThat(editor.problemText().getParent().getCursor().getType()).isEqualTo(java.awt.Cursor.DEFAULT_CURSOR);
    }

    @Test
    void aCenteredRequestCentersTheFieldTextAndTheNextOneDoesNot() {
        editor.open(new InlineEditRequest(AREA, FONT, "1", InlineValidators.anyText(), confirmed::add,
                InlineEditRequest.Anchor.CENTER));
        assertThat(editor.field().getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.CENTER);
        editor.cancel();
        editor.open(request("uno"));
        assertThat(editor.field().getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.LEADING);
    }

    @Test
    void aRightAnchoredFieldGrowsToTheLeftKeepingItsRightEdge() {
        editor.open(new InlineEditRequest(AREA, FONT, "a", InlineValidators.anyText(), confirmed::add,
                InlineEditRequest.Anchor.RIGHT));
        assertThat(editor.field().getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.RIGHT);
        Rectangle narrowField = textArea();
        editor.field().setText("a".repeat(60));
        Rectangle grown = textArea();
        assertThat(grown.x).isLessThan(narrowField.x);
        assertThat(grown.getMaxX()).isEqualTo(narrowField.getMaxX());
        assertThat(editor.confirmButton().getX()).isGreaterThanOrEqualTo(
                editor.field().getX() + editor.field().getWidth());
    }

    @Test
    void aCenteredFieldWiderThanItsAreaStaysCenteredOnIt() {
        Rectangle2D narrow = new Rectangle2D.Double(200, 40, 8, 24);
        editor.open(new InlineEditRequest(narrow, FONT, "1", InlineValidators.anyText(), confirmed::add,
                InlineEditRequest.Anchor.CENTER));
        Rectangle area = textArea();
        double center = canvas.viewport().documentTransform().createTransformedShape(narrow).getBounds2D()
                .getCenterX();
        assertThat(area.getCenterX()).isBetween(center - 1, center + 1);
    }

    @Test
    void confirmOrCancelConfirmsAValidTextAndCancelsAnInvalidOne() {
        editor.confirmOrCancel(); // nothing open: nothing happens
        editor.open(request("uno", NOT_BAD));
        editor.field().setText("dos");
        editor.confirmOrCancel();
        assertThat(confirmed).containsExactly("dos");
        assertThat(editor.isOpen()).isFalse();
        editor.open(request("uno", NOT_BAD));
        editor.field().setText("bad");
        editor.confirmOrCancel();
        assertThat(confirmed).containsExactly("dos");
        assertThat(editor.isOpen()).isFalse();
    }

    @Test
    void aFontChangeConfirmsAnOpenEditAndLeavesNoListenerBehind() {
        int listeners = TextFonts.listenerCount();
        try {
            editor.open(request("uno"));
            editor.field().setText("dos");
            TextFonts.set(new TextFont(Font.SERIF, 14));
            assertThat(confirmed).containsExactly("dos");
            assertThat(editor.isOpen()).isFalse();
            assertThat(TextFonts.listenerCount()).isEqualTo(listeners);

            editor.open(request("tres", NOT_BAD));
            editor.field().setText("bad");
            TextFonts.set(TextFont.DEFAULT);
            assertThat(confirmed).containsExactly("dos"); // an invalid text is cancelled
            assertThat(editor.isOpen()).isFalse();
            assertThat(TextFonts.listenerCount()).isEqualTo(listeners);
        } finally {
            TextFonts.set(TextFont.DEFAULT);
        }
    }

    // --- IA.3: notes of several lines ---

    private InlineEditRequest noteRequest(String text) {
        return InlineEditRequest.multiLine(AREA, FONT, text, InlineValidators.anyText(), confirmed::add);
    }

    private void typeInTheNote(int keyCode, int modifiers) {
        ((InlineTextEditor.Area) editor.area()).deliver(key(editor.area(), keyCode, modifiers));
    }

    private Rectangle noteBounds() {
        return SwingUtilities.convertRectangle(editor.area().getParent(), editor.area().getBounds(), canvas);
    }

    @Test
    void aNoteOpensWithItsLinesAndShiftEnterAddsALineWhileEnterConfirms() {
        editor.open(noteRequest("Prueba:\ncambia A"));

        assertThat(editor.textComponent()).isSameAs(editor.area());
        assertThat(editor.area().isVisible()).isTrue();
        assertThat(editor.field().isVisible()).isFalse();
        assertThat(editor.area().getText()).isEqualTo("Prueba:\ncambia A");
        assertThat(editor.area().getSelectedText()).isEqualTo("Prueba:\ncambia A");

        editor.area().setCaretPosition(editor.area().getDocument().getLength());
        typeInTheNote(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
        assertThat(editor.area().getText()).isEqualTo("Prueba:\ncambia A\n");
        editor.area().append("mira la salida"); // off the EDT the caret does not follow the edit
        assertThat(editor.isOpen()).isTrue();
        assertThat(confirmed).isEmpty();

        typeInTheNote(KeyEvent.VK_ENTER, 0);
        assertThat(editor.isOpen()).isFalse();
        assertThat(confirmed).containsExactly("Prueba:\ncambia A\nmira la salida");
    }

    @Test
    void escapeCancelsANoteAndTabTypesNothing() {
        editor.open(noteRequest("nota"));
        editor.area().setCaretPosition(editor.area().getDocument().getLength());
        typeInTheNote(KeyEvent.VK_TAB, 0);
        assertThat(editor.area().getText()).isEqualTo("nota");
        typeInTheNote(KeyEvent.VK_ESCAPE, 0);
        assertThat(editor.isOpen()).isFalse();
        assertThat(confirmed).isEmpty();
    }

    @Test
    void aNoteEditorKeepsItsTopAndGrowsDownWithEveryLine() {
        editor.open(noteRequest("uno"));
        Rectangle oneLine = noteBounds();
        editor.area().setText("uno\ndos\ntres larga");
        Rectangle threeLines = noteBounds();
        assertThat(threeLines.y).isEqualTo(oneLine.y);
        assertThat(threeLines.x).isEqualTo(oneLine.x);
        assertThat(threeLines.height).isGreaterThan(2 * oneLine.height - 4);
        assertThat(threeLines.width).isGreaterThanOrEqualTo(oneLine.width);
        assertThat(editor.confirmButton().getY()).isLessThan(threeLines.height / 2);
    }

    @Test
    void aOneLineRequestAfterANoteUsesTheFieldAgain() {
        editor.open(noteRequest("nota"));
        editor.open(request("U1"));
        assertThat(confirmed).containsExactly("nota");
        assertThat(editor.textComponent()).isSameAs(editor.field());
        assertThat(editor.field().isVisible()).isTrue();
        assertThat(editor.area().isVisible()).isFalse();
        type(KeyEvent.VK_ENTER);
        assertThat(confirmed).containsExactly("nota", "U1");
    }
}
