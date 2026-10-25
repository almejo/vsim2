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
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The box's spinners resize it like its handle: the drawing stays centred and the pins stay on the grid. Ctrl while
 * dragging the handle scales the drawing with the box instead; the hint line says which of the two is in play. The
 * 6 × 4 box at zoom 1 has its corner at (70, 70) and its handle 18 px beyond the bottom right corner, at (136, 120).
 */
class ShapeBoxResizeTest {

    @BeforeEach
    void speakSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void ctrlWhileDraggingTheBoxHandleScalesTheDrawingWithTheBox() {
        AtomicReference<SymbolDraft> draft = ShapeNodeToolsTest.rectangle();
        ShapeCanvas canvas = ShapeNodeToolsTest.canvas(draft);

        drag(canvas, new Point(136, 120), new Point(184, 152), InputEvent.CTRL_DOWN_MASK);

        assertThat(draft.get().appearance().size()).isEqualTo(new GridSize(12, 8));
        assertThat(ShapeNodeToolsTest.firstPath(draft).nodes().get(2).point()).isEqualTo(new SymbolPoint(96, 64));
    }

    @Test
    void pressingCtrlInTheMiddleOfTheDragSwitchesToScaling() {
        AtomicReference<SymbolDraft> draft = ShapeNodeToolsTest.rectangle();
        ShapeCanvas canvas = ShapeNodeToolsTest.canvas(draft);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
                136, 120, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK,
                184, 152, 0, false, MouseEvent.NOBUTTON));
        assertThat(ShapeNodeToolsTest.firstPath(draft).nodes().get(2).point()).isEqualTo(new SymbolPoint(72, 48));

        ShapeNodeToolsTest.control(canvas, KeyEvent.KEY_PRESSED);

        assertThat(ShapeNodeToolsTest.firstPath(draft).nodes().get(2).point()).isEqualTo(new SymbolPoint(96, 64));
    }

    @Test
    void theHintLineSaysWhetherTheBoxStillDrivesTheShape() {
        ShapeEditorPanel panel = panel();
        assertThat(panel.hintText()).startsWith("Forma predeterminada: sigue el tamaño de la caja");

        panel.apply(draft(panel).applyTemplate(SymbolTemplates.Template.RECTANGLE));

        assertThat(panel.hintText()).startsWith("Ahora es un dibujo: la caja y las figuras se cambian por separado");
    }

    @Test
    void overTheBoxHandleTheHintLineOffersCtrlToScaleTheDrawing() {
        ShapeEditorPanel panel = panel();
        panel.apply(draft(panel).applyTemplate(SymbolTemplates.Template.RECTANGLE));
        ShapeCanvas canvas = panel.canvas();
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        Rectangle2D box = canvas.boxOnScreen();

        ShapeNodeToolsTest.move(canvas, (int) box.getMaxX() + 18, (int) box.getMaxY() + 18, 0);

        assertThat(panel.hintText()).isEqualTo(Messages.text("shape.hint.boxDrawing"));
        ShapeNodeToolsTest.move(canvas, (int) box.getCenterX(), (int) box.getCenterY(), 0);
        assertThat(panel.hintText()).isEqualTo(Messages.text("shape.hint.selectDrawing"));
    }

    private static void drag(ShapeCanvas canvas, Point from, Point to, int modifiers) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0,
                modifiers | InputEvent.BUTTON1_DOWN_MASK, from.x, from.y, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0,
                modifiers | InputEvent.BUTTON1_DOWN_MASK, to.x, to.y, 0, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, modifiers, to.x, to.y, 1, false,
                MouseEvent.BUTTON1));
    }

    @Test
    void theSpinnersKeepTheDrawingCentredForEvenAndOddChanges() {
        ShapeEditorPanel panel = panel();
        panel.apply(draft(panel).applyTemplate(SymbolTemplates.Template.RECTANGLE));

        panel.widthSpinner().setValue(7);

        assertThat(panel.appearance().size()).isEqualTo(new GridSize(7, 4));
        assertThat(firstNode(panel)).isEqualTo(new SymbolPoint(4, 0));
        assertThat(panel.appearance().pins()).containsExactly(new PinPlacement(
                ShapeEditorPanelTest.definition().pinout().getFirst().source(), new GridPoint(0, 2)));

        panel.heightSpinner().setValue(6);

        assertThat(firstNode(panel)).isEqualTo(new SymbolPoint(4, 8));
        assertThat(panel.appearance().pins().getFirst().offset()).isEqualTo(new GridPoint(0, 3));
    }

    private static SymbolPoint firstNode(ShapeEditorPanel panel) {
        return ((SymbolPath) panel.appearance().drawing().orElseThrow().elements().getFirst()).nodes().getFirst()
                .point();
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
