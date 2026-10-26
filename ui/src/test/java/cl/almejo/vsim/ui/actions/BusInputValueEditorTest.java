package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.inline.InlineTextEditor;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.InterfacePinPainter;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/** M7: «Valor…» types a value into a bus input pin, in place over its value box; it is not a document change. */
class BusInputValueEditorTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private EditorView view;
    private InlineTextEditor editor;
    private LabelActions actions;

    @BeforeEach
    void create() {
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of()),
                NO_WINDOW);
        view.canvas().setSize(800, 600);
        editor = view.inlineEditor();
        actions = view.labelFeatures().actions();
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    /** A 4-bit input pin at (10, 10) touching a 4-bit output pin; the input pin once its value is live. */
    private Component inputWithOutput(Map<String, Object> parameters) {
        Component input = place(openDocument, StandardComponentTypes.INPUT_PIN, 10, 10, parameters);
        place(openDocument, StandardComponentTypes.OUTPUT_PIN, 12, 10, Map.of("width", parameters.get("width")));
        waitUntil(() -> openDocument.latestSnapshot().componentState(input.id().value())
                instanceof VisibleState.SwitchState);
        return input;
    }

    private VisibleState stateOf(Component component) {
        return openDocument.latestSnapshot().componentState(component.id().value());
    }

    private Component outputPin() {
        return openDocument.document().components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.OUTPUT_PIN))
                .findFirst().orElseThrow();
    }

    @Test
    void typingAValueSetsTheLiveValueAndAddsNoUndoStep() {
        Component input = inputWithOutput(Map.of("width", 4));
        Object undo = openDocument.history().undoDescription();
        long changes = openDocument.changeCount();

        actions.editValue(input);
        assertThat(editor.isOpen()).isTrue();
        assertThat(editor.field().getText()).isEqualTo("0b0000");
        editor.field().setText("0x5");
        assertThat(editor.problemText().isVisible()).isFalse();
        editor.confirm();

        assertThat(editor.isOpen()).isFalse();
        waitUntil(() -> stateOf(outputPin()).equals(new VisibleState.LedState(Value.of(4, 5))));
        assertThat(openDocument.history().undoDescription()).isEqualTo(undo);
        assertThat(openDocument.changeCount()).isEqualTo(changes);
        assertThat(openDocument.document().component(input.id()).orElseThrow().parameters().get("value"))
                .isEqualTo("0");
    }

    /**
     * I2 (M7 fix round): the first click of a double click on the tag toggles the pin; the editor must still open on
     * the value from before the double click, and Esc must leave that value.
     */
    @Test
    void aDoubleClickOnTheTagEditsTheValueFromBeforeTheDoubleClick() {
        Component input = inputWithOutput(Map.of("width", 4));
        Component marker = place(openDocument, StandardComponentTypes.INPUT_PIN, 30, 30, Map.of("width", 4));
        openDocument.setInputValue(input.id(), Value.of(4, 5));
        waitUntil(() -> stateOf(input).equals(new VisibleState.SwitchState(Value.of(4, 5))));

        doubleClickAt(10.6, 10.6);

        assertThat(editor.isOpen()).isTrue();
        assertThat(editor.field().getText()).isEqualTo("0b0101");
        editor.cancel();
        settle(marker, 3);
        assertThat(stateOf(input)).isEqualTo(new VisibleState.SwitchState(Value.of(4, 5)));

        doubleClickAt(10.6, 10.6);
        assertThat(editor.field().getText()).isEqualTo("0b0101");
        editor.field().setText("0x9");
        editor.confirm();
        settle(marker, 4);
        assertThat(stateOf(input)).isEqualTo(new VisibleState.SwitchState(Value.of(4, 9)));
    }

    /** M8 (M7 fix round): the value box is drawn over a wire passing under it, so a double click there edits the value. */
    @Test
    void aDoubleClickOnTheValueBoxOverAWireEditsTheValue() {
        Component input = inputWithOutput(Map.of("width", 8));
        Rectangle2D box = InterfacePinPainter.valueBox(openDocument.registry().typeOf(input), input).orElseThrow();
        double centerX = box.getCenterX() / GridPoint.PIXELS_PER_UNIT;
        int wireY = (int) Math.round(box.getCenterY() / GridPoint.PIXELS_PER_UNIT);
        openDocument.execute(new AddElementsCommand(List.of(new Wire(openDocument.document().newWireId(),
                new GridPoint((int) Math.floor(centerX) - 3, wireY), new GridPoint((int) Math.ceil(centerX) + 3, wireY)))));
        assertThat(box.contains(centerX * GridPoint.PIXELS_PER_UNIT, wireY * GridPoint.PIXELS_PER_UNIT)).isTrue();

        doubleClickAt(centerX, wireY);

        assertThat(editor.isOpen()).isTrue();
        assertThat(view.canvas().selection().ids()).containsExactly(input.id());
        editor.cancel();
    }

    private void doubleClickAt(double gridX, double gridY) {
        Point2D screen = view.canvas().viewport().gridToScreen(gridX, gridY);
        int x = (int) Math.round(screen.getX());
        int y = (int) Math.round(screen.getY());
        var listener = view.canvas().getMouseListeners()[0];
        for (int clickCount = 1; clickCount <= 2; clickCount++) {
            listener.mousePressed(new MouseEvent(view.canvas(), MouseEvent.MOUSE_PRESSED, 0,
                    InputEvent.BUTTON1_DOWN_MASK, x, y, clickCount, false, MouseEvent.BUTTON1));
            listener.mouseReleased(new MouseEvent(view.canvas(), MouseEvent.MOUSE_RELEASED, 0, 0, x, y, clickCount,
                    false, MouseEvent.BUTTON1));
        }
    }

    /** Queues a value for {@code marker} and waits for it: every request queued before has been applied by then. */
    private void settle(Component marker, int value) {
        openDocument.setInputValue(marker.id(), Value.of(4, value));
        waitUntil(() -> stateOf(marker).equals(new VisibleState.SwitchState(Value.of(4, value))));
    }

    @Test
    void theEditorStartsWithTheLiveValueInThePinsFormat() {
        Component input = inputWithOutput(Map.of("width", 8, "format", "signed"));
        openDocument.setInputValue(input.id(), Value.of(8, 0xFD));
        waitUntil(() -> stateOf(input).equals(new VisibleState.SwitchState(Value.of(8, 0xFD))));
        actions.editValue(input);
        assertThat(editor.field().getText()).isEqualTo("-3");
        assertThat(editor.field().getSelectedText()).isEqualTo("-3");
    }

    @Test
    void aNegativeNumberIsTakenInTwosComplement() {
        Component input = inputWithOutput(Map.of("width", 4, "format", "signed"));
        actions.editValue(input);
        editor.field().setText("-3");
        editor.confirm();
        waitUntil(() -> stateOf(outputPin()).equals(new VisibleState.LedState(Value.of(4, 0xD))));
    }

    @Test
    void aValueThatDoesNotFitCannotBeConfirmed() {
        Component input = inputWithOutput(Map.of("width", 4));
        actions.editValue(input);
        editor.field().setText("0x1F");
        assertThat(editor.problemText().isVisible()).isTrue();
        assertThat(editor.problemText().getText()).isEqualTo("No cabe en 4 bits");
        editor.confirm();
        editor.confirmButton().doClick();
        assertThat(editor.isOpen()).isTrue();
        editor.field().setText("-9");
        assertThat(editor.problemText().getText()).isEqualTo("No cabe en 4 bits");
        editor.field().setText("doce");
        assertThat(editor.problemText().getText()).isEqualTo("Valor no válido (usa 0b…, 0x… o un número)");
        editor.cancel();
        assertThat(stateOf(input)).isEqualTo(new VisibleState.SwitchState(Value.of(4, 0)));
    }

    @Test
    void theEditorSitsOverTheValueBox() {
        Component input = inputWithOutput(Map.of("width", 8));
        Rectangle2D box = InterfacePinPainter.valueBox(openDocument.registry().typeOf(input), input).orElseThrow();
        actions.editValue(input);
        Rectangle field = SwingUtilities.convertRectangle(editor.field().getParent(), editor.field().getBounds(),
                view.canvas());
        Rectangle2D painted = view.canvas().viewport().documentTransform().createTransformedShape(box).getBounds2D();
        // centred on the box (the field is at least as tall as the font allows)
        assertThat(field.getCenterX()).isBetween(painted.getCenterX() - 2, painted.getCenterX() + 2);
        assertThat(field.getCenterY()).isBetween(painted.getCenterY() - 2, painted.getCenterY() + 2);
        editor.cancel();
    }

    @Test
    void aWidthChangedMeanwhileIsTakenIntoAccount() {
        Component input = inputWithOutput(Map.of("width", 8));
        actions.editValue(input);
        editor.field().setText("0xFF");
        assertThat(editor.problemText().isVisible()).isFalse();
        ComponentType type = openDocument.registry().typeOf(input);
        openDocument.execute(new ChangeParametersCommand(input.id(), type, Map.of("value", "0", "width", 4)));
        assertThat(editor.problemText().getText()).isEqualTo("No cabe en 4 bits");
    }

    @Test
    void aValueConfirmedAfterTheWidthChangedHasTheNewWidth() {
        Component input = inputWithOutput(Map.of("width", 8));
        actions.editValue(input);
        ComponentType type = openDocument.registry().typeOf(input);
        openDocument.execute(new ChangeParametersCommand(input.id(), type, Map.of("value", "0", "width", 4)));
        waitUntil(() -> stateOf(input).equals(new VisibleState.SwitchState(Value.of(4, 0))));
        editor.field().setText("-1");
        editor.confirm();
        waitUntil(() -> stateOf(input).equals(new VisibleState.SwitchState(Value.of(4, 0xF))));
    }

    @Test
    void oneBitPinsAndOtherComponentsGetNoValueEditor() {
        Component oneBit = place(openDocument, StandardComponentTypes.INPUT_PIN, 0, 0, Map.of());
        actions.editValue(oneBit);
        assertThat(editor.isOpen()).isFalse();
        Component output = place(openDocument, StandardComponentTypes.OUTPUT_PIN, 0, 10, Map.of("width", 8));
        actions.editValue(output);
        assertThat(editor.isOpen()).isFalse();
    }

    @Test
    void theContextMenuOffersValueOnASingleBusInputPinOnly() {
        Component input = inputWithOutput(Map.of("width", 4));
        LabelMenuContributor contributor = new LabelMenuContributor(actions);
        view.canvas().selection().select(input.id());
        JPopupMenu menu = menuFor(contributor, input);
        assertThat(texts(menu)).containsExactly("Valor…", "Etiqueta…");
        JMenuItem value = (JMenuItem) menu.getComponent(0);
        value.getAction().actionPerformed(new ActionEvent(value, ActionEvent.ACTION_PERFORMED, ""));
        assertThat(editor.isOpen()).isTrue();
        editor.cancel();

        Component oneBit = place(openDocument, StandardComponentTypes.INPUT_PIN, 0, 0, Map.of());
        view.canvas().selection().select(oneBit.id());
        assertThat(texts(menuFor(contributor, oneBit))).containsExactly("Etiqueta…");
        view.canvas().selection().select(outputPin().id());
        assertThat(texts(menuFor(contributor, outputPin()))).containsExactly("Etiqueta…");
    }

    @Test
    void theFactoryPutsValueFirstAmongTheComponentEntries() {
        Component input = inputWithOutput(Map.of("width", 4));
        view.canvas().selection().select(input.id());
        JPopupMenu menu = view.contextMenu(Optional.of(input), new Point2D.Double(11, 11));
        assertThat(texts(menu).getFirst()).isEqualTo("Valor…");
    }

    private JPopupMenu menuFor(LabelMenuContributor contributor, DocumentElement target) {
        JPopupMenu menu = new JPopupMenu();
        contributor.contribute(menu, Optional.of(target), view.canvas().selection(), new Point2D.Double(0, 0));
        return menu;
    }
}
