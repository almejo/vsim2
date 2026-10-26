package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The box's spinners resize it like its handle: what is on an edge moves with it, the rest stays centred and the pins
 * stay on the grid. Ctrl while dragging the handle scales the whole drawing with the box instead. The 6 × 4 box at
 * zoom 1 has its corner at (70, 70) and its handle 18 px beyond the bottom right corner, at (136, 120).
 */
class ShapeBoxResizeTest {
    private static final SymbolPath INNER_RECTANGLE = SymbolTemplates.rectangle(new SymbolPoint(8, 8),
            new SymbolPoint(40, 24), false);

    @Test
    void ctrlWhileDraggingTheBoxHandleScalesTheDrawingWithTheBox() {
        AtomicReference<SymbolDraft> draft = innerRectangle();
        ShapeCanvas canvas = ShapeNodeToolsTest.canvas(draft);

        drag(canvas, new Point(136, 120), new Point(184, 152), InputEvent.CTRL_DOWN_MASK);

        assertThat(draft.get().appearance().size()).isEqualTo(new GridSize(12, 8));
        assertThat(ShapeNodeToolsTest.firstPath(draft).nodes().get(2).point()).isEqualTo(new SymbolPoint(80, 48));
    }

    @Test
    void pressingCtrlInTheMiddleOfTheDragSwitchesToScaling() {
        AtomicReference<SymbolDraft> draft = innerRectangle();
        ShapeCanvas canvas = ShapeNodeToolsTest.canvas(draft);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
                136, 120, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK,
                184, 152, 0, false, MouseEvent.NOBUTTON));
        assertThat(ShapeNodeToolsTest.firstPath(draft).nodes().get(2).point()).isEqualTo(new SymbolPoint(64, 40));

        ShapeNodeToolsTest.control(canvas, KeyEvent.KEY_PRESSED);

        assertThat(ShapeNodeToolsTest.firstPath(draft).nodes().get(2).point()).isEqualTo(new SymbolPoint(80, 48));
    }

    @Test
    void theSpinnersKeepAnInnerDrawingCentredForEvenAndOddChanges() {
        ShapeEditorPanel panel = panel();
        panel.apply(draft(panel).applyTemplate(SymbolTemplates.Template.MUX).addFigure(INNER_RECTANGLE));

        panel.widthSpinner().setValue(7);

        assertThat(panel.appearance().size()).isEqualTo(new GridSize(7, 4));
        assertThat(lastFigureCorner(panel)).isEqualTo(new SymbolPoint(12, 8));
        assertThat(panel.appearance().pins()).containsExactly(new PinPlacement(
                ShapeEditorPanelTest.definition().pinout().getFirst().source(), new GridPoint(0, 2)));

        panel.heightSpinner().setValue(6);

        assertThat(lastFigureCorner(panel)).isEqualTo(new SymbolPoint(12, 16));
        assertThat(panel.appearance().pins().getFirst().offset()).isEqualTo(new GridPoint(0, 3));
    }

    @Test
    void theSpinnersMoveWhatIsOnTheBoxEdgesWithThem() {
        ShapeEditorPanel panel = panel();

        panel.widthSpinner().setValue(7);
        panel.heightSpinner().setValue(6);

        assertThat(((SymbolPath) panel.appearance().drawing().orElseThrow().elements().getFirst()).nodes().get(2)
                .point()).isEqualTo(new SymbolPoint(56, 48));
    }

    private static void drag(ShapeCanvas canvas, Point from, Point to, int modifiers) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0,
                modifiers | InputEvent.BUTTON1_DOWN_MASK, from.x, from.y, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0,
                modifiers | InputEvent.BUTTON1_DOWN_MASK, to.x, to.y, 0, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, modifiers, to.x, to.y, 1, false,
                MouseEvent.BUTTON1));
    }

    private static SymbolPoint lastFigureCorner(ShapeEditorPanel panel) {
        return ((SymbolPath) panel.appearance().drawing().orElseThrow().elements().getLast()).nodes().getFirst()
                .point();
    }

    /** A rectangle clear of every edge of the 6 × 4 box, so a resize without Ctrl only centres it. */
    private static AtomicReference<SymbolDraft> innerRectangle() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        return new AtomicReference<>(SymbolDraft.of(ComponentAppearance.of(definition), definition.pinout(), false)
                .addFigure(INNER_RECTANGLE));
    }

    private static ShapeEditorPanel panel() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        return new ShapeEditorPanel(new ShapeEditRequest(definition, ComponentAppearance.of(definition),
                ShapeEditScope.INSTANCE, true, false, "", 1, "U1", false, false), AppPreferences.inMemory(),
                () -> ColorSchemes.LIGHT);
    }

    private static SymbolDraft draft(ShapeEditorPanel panel) {
        return SymbolDraft.of(panel.appearance(), ShapeEditorPanelTest.definition().pinout(), false);
    }
}
