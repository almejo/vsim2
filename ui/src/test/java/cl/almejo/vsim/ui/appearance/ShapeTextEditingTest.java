package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared inline editor over the shape canvas: several lines, colours kept, a blank text is no text. */
class ShapeTextEditingTest {
    private static final SymbolText UNO = new SymbolText(new SymbolPoint(8, 8), Optional.of("uno"), TextSize.MEDIUM,
            TextAlignment.LEFT, NoteColor.BLUE, NoteColor.GRAY);

    private final ComponentDefinition definition = ShapeEditorPanelTest.definition();
    private final List<SymbolDraft> published = new ArrayList<>();
    private final AtomicReference<SymbolDraft> draft = new AtomicReference<>();

    @Test
    void theTextIsEditedInTheSharedInlineEditorInItsMultiLineMode() {
        ShapeCanvas canvas = canvasWith(UNO);
        editTextAt(canvas);
        JTextArea area = area(canvas);

        assertThat(canvas.textEditor().isOpen()).isTrue();
        assertThat(canvas.textEditor().textComponent()).isSameAs(area);
        assertThat(area.isVisible()).isTrue();
        assertThat(canvas.textEditor().field().isVisible()).isFalse();
        assertThat(area.getText()).isEqualTo("uno");
        assertThat(area.getSelectedText()).as("all selected on open").isEqualTo("uno");
        assertThat(SwingUtilities.isDescendingFrom(area, canvas)).isTrue();
    }

    @Test
    void enterConfirmsShiftEnterAddsALineAndEscCancels() {
        ShapeCanvas canvas = canvasWith(UNO);
        editTextAt(canvas);
        JTextArea area = area(canvas);

        assertThat(area.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("shift ENTER")))
                .as("Shift+Enter is the area's own insert-break").isEqualTo("insert-break");
        area.setText("uno\ndos");
        press(area, "ENTER");

        SymbolText stored = textOf(draft.get());
        assertThat(stored.text()).contains("uno\ndos");
        assertThat(stored.color()).isEqualTo(NoteColor.BLUE);
        assertThat(stored.border()).isEqualTo(NoteColor.GRAY);
        assertThat(canvas.operationActive()).isFalse();

        editTextAt(canvas);
        area(canvas).setText("otra");
        press(area(canvas), "ESCAPE");

        assertThat(textOf(draft.get()).text()).as("Esc cancels").contains("uno\ndos");
        assertThat(canvas.operationActive()).isFalse();
        assertThat(published).hasSize(1);
    }

    @Test
    void theEditorFollowsThePanAndZoomOfTheCanvas() {
        ShapeCanvas canvas = canvasWith(UNO);
        editTextAt(canvas);
        int before = canvas.textEditor().textComponent().getParent().getX();

        canvas.setZoom(2);

        assertThat(canvas.textEditor().textComponent().getParent().getX()).isNotEqualTo(before);
    }

    @Test
    void aTextEditedToBlankIsRemovedInTheSameStepAsTheEdit() {
        ShapeCanvas canvas = canvasWith(UNO);
        SymbolDraft before = draft.get();
        editTextAt(canvas);
        area(canvas).setText("  \n ");

        canvas.finishOperation();

        assertThat(draft.get().elements()).noneMatch(SymbolText.class::isInstance);
        assertThat(published).as("one published draft: one undo step").hasSize(1);
        assertThat(before.elements()).anyMatch(SymbolText.class::isInstance);
    }

    @Test
    void leavingTheAreaBlankByAToolChangeAlsoRemovesTheText() {
        ShapeCanvas canvas = canvasWith(UNO);
        editTextAt(canvas);
        area(canvas).setText("");

        canvas.finishOperation();

        assertThat(draft.get().elements()).noneMatch(SymbolText.class::isInstance);
        assertThat(published).hasSize(1);
    }

    @Test
    void aTypedTextEscapedWithContentIsKeptAsItWas() {
        ShapeCanvas canvas = canvasWith(UNO);
        editTextAt(canvas);
        area(canvas).setText("otra");

        canvas.escapeOperation();

        assertThat(textOf(draft.get()).text()).contains("uno");
        assertThat(published).isEmpty();
    }

    @Test
    void aNewTextNeverTypedIntoVanishesWhenYouLeaveIt() {
        ShapeCanvas canvas = canvasWith(UNO);
        SymbolDraft before = draft.get();
        canvas.setTool(ShapeTool.TEXT);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, 0, 200, 150, 1, false,
                MouseEvent.BUTTON1));
        assertThat(canvas.operationActive()).isTrue();

        canvas.setTool(ShapeTool.SELECT);

        assertThat(canvas.operationActive()).isFalse();
        assertThat(draft.get()).isSameAs(before);
        assertThat(published).isEmpty();
    }

    @Test
    void aNewBlankTextIsNotAddedWhenTheAreaIsFinished() {
        ShapeCanvas canvas = canvasWith(UNO);
        SymbolDraft before = draft.get();
        canvas.setTool(ShapeTool.TEXT);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, 0, 200, 150, 1, false,
                MouseEvent.BUTTON1));
        area(canvas).setText("   ");

        canvas.finishOperation();

        assertThat(draft.get()).isSameAs(before);
        assertThat(published).isEmpty();
    }

    @Test
    void aTextThatFollowsTheComponentNameIsNotRemovedBeingLeftUntouched() {
        SymbolText followsName = new SymbolText(new SymbolPoint(8, 8), Optional.empty(), TextSize.MEDIUM,
                TextAlignment.CENTER);
        ShapeCanvas canvas = canvasWith(followsName);
        editTextAt(canvas);

        canvas.finishOperation();

        assertThat(textOf(draft.get())).isEqualTo(followsName);
    }

    private ShapeCanvas canvasWith(SymbolText text) {
        draft.set(SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .addText(text));
        ShapeCanvas canvas = new ShapeCanvas(definition, draft::get, next -> {
            published.add(next);
            draft.set(next);
        }, draft::set, () -> ColorSchemes.LIGHT, () -> { });
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        return canvas;
    }

    private static void editTextAt(ShapeCanvas canvas) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, 0, 78, 78, 2, false,
                MouseEvent.BUTTON1));
        assertThat(canvas.operationActive()).isTrue();
    }

    private static JTextArea area(ShapeCanvas canvas) {
        return canvas.textEditor().area();
    }

    /** Runs the action the area binds to {@code stroke}, as the focused area would. */
    private static void press(JTextArea area, String stroke) {
        Object binding = area.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(stroke));
        assertThat(binding).as("binding for %s", stroke).isNotNull();
        area.getActionMap().get(binding).actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, stroke));
    }

    private static SymbolText textOf(SymbolDraft draft) {
        return draft.elements().stream().filter(SymbolText.class::isInstance).map(SymbolText.class::cast).findFirst()
                .orElseThrow();
    }
}
