package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Shift while moving locks the move to the dominant axis; the check is live during the drag. */
class ShapeShiftMoveTest {
    private static final int SHIFT = MouseEvent.SHIFT_DOWN_MASK;
    private static final int LEFT = MouseEvent.BUTTON1_DOWN_MASK;
    private static final Point INSIDE_FIGURE = new Point(86, 86);

    @Test
    void shiftDraggingAFigureDiagonallyMovesItOnlyAlongTheDominantAxis() {
        AtomicReference<SymbolDraft> draft = figureDraft();
        ShapeCanvas canvas = canvas(draft);

        press(canvas, INSIDE_FIGURE, 0);
        drag(canvas, new Point(INSIDE_FIGURE.x + 16, INSIDE_FIGURE.y + 4), SHIFT);
        release(canvas, new Point(INSIDE_FIGURE.x + 16, INSIDE_FIGURE.y + 4));

        assertThat(firstPoint(draft)).isEqualTo(new SymbolPoint(8 + 16, 8));

        draft.set(figureDraft().get());
        press(canvas, INSIDE_FIGURE, 0);
        drag(canvas, new Point(INSIDE_FIGURE.x + 4, INSIDE_FIGURE.y + 8), SHIFT);
        release(canvas, new Point(INSIDE_FIGURE.x + 4, INSIDE_FIGURE.y + 8));

        assertThat(firstPoint(draft)).isEqualTo(new SymbolPoint(8, 8 + 8));
    }

    @Test
    void withoutShiftTheFigureMovesFreely() {
        AtomicReference<SymbolDraft> draft = figureDraft();
        ShapeCanvas canvas = canvas(draft);

        press(canvas, INSIDE_FIGURE, 0);
        drag(canvas, new Point(INSIDE_FIGURE.x + 16, INSIDE_FIGURE.y + 8), 0);
        release(canvas, new Point(INSIDE_FIGURE.x + 16, INSIDE_FIGURE.y + 8));

        assertThat(firstPoint(draft)).isEqualTo(new SymbolPoint(8 + 16, 8 + 8));
    }

    @Test
    void releasingShiftInTheMiddleOfTheDragFreesTheMoveAndPressingItLocksAgain() {
        AtomicReference<SymbolDraft> draft = figureDraft();
        ShapeCanvas canvas = canvas(draft);
        Point target = new Point(INSIDE_FIGURE.x + 16, INSIDE_FIGURE.y + 8);

        press(canvas, INSIDE_FIGURE, 0);
        drag(canvas, target, SHIFT);
        assertThat(firstPoint(draft)).isEqualTo(new SymbolPoint(8 + 16, 8));

        key(canvas, KeyEvent.KEY_RELEASED, 0);
        assertThat(firstPoint(draft)).as("released Shift, pointer still").isEqualTo(new SymbolPoint(8 + 16, 8 + 8));

        key(canvas, KeyEvent.KEY_PRESSED, SHIFT);
        assertThat(firstPoint(draft)).as("pressed Shift, pointer still").isEqualTo(new SymbolPoint(8 + 16, 8));
        release(canvas, target);
    }

    @Test
    void shiftWithoutADragChangesNothing() {
        AtomicReference<SymbolDraft> draft = figureDraft();
        ShapeCanvas canvas = canvas(draft);
        SymbolDraft before = draft.get();

        key(canvas, KeyEvent.KEY_PRESSED, SHIFT);
        key(canvas, KeyEvent.KEY_RELEASED, 0);

        assertThat(draft.get()).isSameAs(before);
    }

    @Test
    void shiftClickWithoutADragTogglesTheSelection() {
        AtomicReference<SymbolDraft> draft = figureDraft();
        ShapeCanvas canvas = canvas(draft);
        SymbolPath before = (SymbolPath) draft.get().elements().getFirst();

        press(canvas, INSIDE_FIGURE, SHIFT);
        release(canvas, INSIDE_FIGURE);

        assertThat(draft.get().selection()).isEmpty();
        assertThat(draft.get().elements().getFirst()).isEqualTo(before);

        press(canvas, INSIDE_FIGURE, SHIFT);
        release(canvas, INSIDE_FIGURE);
        assertThat(draft.get().selection()).containsExactly(0);
    }

    @Test
    void theDragTipTellsAboutShiftWhileMoving() {
        AtomicReference<SymbolDraft> draft = figureDraft();
        ShapeCanvas canvas = canvas(draft);

        press(canvas, INSIDE_FIGURE, 0);
        drag(canvas, new Point(INSIDE_FIGURE.x + 8, INSIDE_FIGURE.y + 8), 0);

        assertThat(canvas.dragTip().orElseThrow()).contains(Messages.text("shape.drag.axis"));
        release(canvas, INSIDE_FIGURE);
        assertThat(canvas.dragTip()).isEmpty();
    }

    @Test
    void shiftDraggingANodeKeepsItOnItsRowOrColumn() {
        AtomicReference<SymbolDraft> draft = figureDraft();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.NODES);

        press(canvas, new Point(78, 78), 0);
        drag(canvas, new Point(78 + 8, 78 + 3), SHIFT);
        release(canvas, new Point(78 + 8, 78 + 3));

        assertThat(firstPoint(draft)).isEqualTo(new SymbolPoint(16, 8));
    }

    @Test
    void shiftDraggingAPinKeepsItOnItsRowOrColumn() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        AtomicReference<SymbolDraft> draft = new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition),
                definition.pinout(), false));
        ShapeCanvas canvas = new ShapeCanvas(definition, draft::get, draft::set, draft::set,
                () -> ColorSchemes.LIGHT, () -> { });
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        GridPoint original = draft.get().appearance().pins().getFirst().offset();
        Point pinScreen = new Point(70 + original.x() * 8, 70 + original.y() * 8);

        press(canvas, pinScreen, 0);
        drag(canvas, new Point(pinScreen.x + 4, pinScreen.y + 16), SHIFT);
        release(canvas, new Point(pinScreen.x + 4, pinScreen.y + 16));

        GridPoint moved = draft.get().appearance().pins().getFirst().offset();
        assertThat(moved.x()).isEqualTo(original.x());
        assertThat(moved.y()).isNotEqualTo(original.y());
    }

    private static SymbolPoint firstPoint(AtomicReference<SymbolDraft> draft) {
        return ((SymbolPath) draft.get().elements().getFirst()).nodes().getFirst().point();
    }

    private static AtomicReference<SymbolDraft> figureDraft() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        SymbolDraft base = SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .applyTemplate(SymbolTemplates.Template.RECTANGLE).select(Set.of(0, 1)).deleteSelection();
        SymbolPath figure = SymbolTemplates.rectangle(new SymbolPoint(8, 8), new SymbolPoint(24, 24), false);
        return new AtomicReference<>(base.addFigure(figure).select(Set.of(0)));
    }

    private static ShapeCanvas canvas(AtomicReference<SymbolDraft> draft) {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        ShapeCanvas canvas = new ShapeCanvas(definition, draft::get, draft::set, draft::set,
                () -> ColorSchemes.LIGHT, () -> { });
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        return canvas;
    }

    private static void press(ShapeCanvas canvas, Point at, int shift) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, LEFT | shift, at.x, at.y, 1, false,
                MouseEvent.BUTTON1));
    }

    private static void drag(ShapeCanvas canvas, Point at, int shift) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, LEFT | shift, at.x, at.y, 0, false,
                MouseEvent.NOBUTTON));
    }

    private static void release(ShapeCanvas canvas, Point at) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, at.x, at.y, 1, false,
                MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, 0, at.x, at.y, 1, false,
                MouseEvent.BUTTON1));
    }

    private static void key(ShapeCanvas canvas, int id, int modifiers) {
        // Not dispatchEvent: a canvas that is not showing has no focus, and the focus manager would drop the event.
        KeyEvent event = new KeyEvent(canvas, id, 0, modifiers, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : canvas.getKeyListeners()) {
            if (id == KeyEvent.KEY_PRESSED) { listener.keyPressed(event); } else { listener.keyReleased(event); }
        }
    }
}
