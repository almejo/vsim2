package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.canvas;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.click;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.firstPath;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.move;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.paint;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.rectangle;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Curvar tramo» (C): the segment under the pointer lights up, a click curves it and shows one handle on the curve at
 * its middle, dragging the handle makes the curve pass through the pointer, Ctrl+click straightens. The Rectángulo
 * template on a 6 × 4 box at zoom 1, corner at (70, 70): its top segment goes from (70, 70) to (118, 70).
 */
class ShapeCurveToolTest {

    @Test
    void theSegmentUnderThePointerIsHighlighted() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);

        move(canvas, 70, 90, 0);

        assertThat(paint(canvas).getRGB(70, 90)).isEqualTo(ColorSchemes.LIGHT.selection().getRGB());
    }

    @Test
    void aClickCurvesAStraightSegmentAndShowsOneHandleOnTheCurveAtItsMiddle() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);

        click(canvas, 94, 70, 0);

        SymbolPath path = firstPath(draft);
        assertThat(path.nodes().getFirst().control()).isPresent();
        SymbolPoint middle = path.segmentMiddle(0);
        assertThat(middle.y()).as("bulges into the box, the only side with room").isPositive();
        assertThat(canvas.curveHandles()).containsExactly(middle);
    }

    @Test
    void draggingTheHandleMakesTheCurvePassThroughThePointer() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);
        click(canvas, 94, 70, 0);
        SymbolPoint handle = firstPath(draft).segmentMiddle(0);

        drag(canvas, 70 + handle.x(), 70 + handle.y(), 94, 86, 0);

        assertThat(firstPath(draft).segmentMiddle(0)).isEqualTo(new SymbolPoint(24, 16));
        assertThat(firstPath(draft).nodes().getFirst().control()).contains(new SymbolPoint(24, 32));
    }

    @Test
    void theCurveControlIsKeptInsideTheBox() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);
        click(canvas, 94, 70, 0);
        SymbolPoint handle = firstPath(draft).segmentMiddle(0);

        drag(canvas, 70 + handle.x(), 70 + handle.y(), 94, 102, 0);

        assertThat(firstPath(draft).nodes().getFirst().control()).contains(new SymbolPoint(24, 32));
    }

    @Test
    void pressingOnAStraightSegmentAndDraggingCurvesItThroughThePointer() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);

        drag(canvas, 70, 86, 78, 86, 0);

        assertThat(firstPath(draft).segmentMiddle(3)).isEqualTo(new SymbolPoint(8, 16));
    }

    @Test
    void ctrlClickStraightensACurvedSegment() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);
        click(canvas, 94, 70, 0);
        SymbolPoint handle = firstPath(draft).segmentMiddle(0);

        click(canvas, 70 + handle.x(), 70 + handle.y(), InputEvent.CTRL_DOWN_MASK);

        assertThat(firstPath(draft).nodes().getFirst().control()).isEmpty();
        assertThat(canvas.curveHandles()).isEmpty();
    }

    @Test
    void theNodesToolDragsTheSameOnCurveHandle() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);
        click(canvas, 94, 70, 0);
        SymbolPoint handle = firstPath(draft).segmentMiddle(0);
        canvas.setTool(ShapeTool.NODES);

        drag(canvas, 70 + handle.x(), 70 + handle.y(), 94, 86, 0);

        assertThat(firstPath(draft).segmentMiddle(0)).isEqualTo(new SymbolPoint(24, 16));
    }

    private static void drag(ShapeCanvas canvas, int fromX, int fromY, int toX, int toY, int modifiers) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0,
                modifiers | InputEvent.BUTTON1_DOWN_MASK, fromX, fromY, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0,
                modifiers | InputEvent.BUTTON1_DOWN_MASK, toX, toY, 0, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, modifiers, toX, toY, 1, false,
                MouseEvent.BUTTON1));
    }
}
