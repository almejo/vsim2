package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.CurvePoint;
import cl.almejo.vsim.core.component.appearance.SegmentCurve;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
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
import static org.assertj.core.api.Assertions.within;

/**
 * «Curvar tramo» (C): the segment under the pointer lights up, a click curves a straight one into a cubic with its
 * handles at a third and two thirds, dragging the curve body bends it through the pointer, Ctrl+click straightens. The
 * Rectángulo template on a 6 × 4 box at zoom 1, corner at (70, 70), one eighth per pixel: its top segment goes from
 * (70, 70) to (118, 70).
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
    void aClickCurvesAStraightSegmentIntoACubicWithHandlesAtAThirdAndTwoThirds() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);

        click(canvas, 94, 70, 0);

        SegmentCurve.Cubic cubic = (SegmentCurve.Cubic) firstPath(draft).nodes().getFirst().curve().orElseThrow();
        assertThat(cubic.first().x()).isEqualTo(16);
        assertThat(cubic.second().x()).isEqualTo(32);
        assertThat(cubic.first().y()).as("bulges into the box, the only side with room").isPositive()
                .isEqualTo(cubic.second().y());
    }

    @Test
    void draggingTheCurveBodyBendsItThroughThePointer() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);

        drag(canvas, 94, 70, 94, 86, 0);

        CurvePoint middle = firstPath(draft).pointAt(0, 0.5);
        assertThat(middle.x()).isCloseTo(24, within(1.0));
        assertThat(middle.y()).isCloseTo(16, within(1.0));
    }

    @Test
    void draggingNearOneEndMovesThatEndsHandleMore() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);

        drag(canvas, 80, 70, 80, 78, 0);

        SegmentCurve.Cubic cubic = (SegmentCurve.Cubic) firstPath(draft).nodes().getFirst().curve().orElseThrow();
        assertThat(cubic.first().y()).isGreaterThan(cubic.second().y());
    }

    @Test
    void theHandlesOfABentCurveStayInsideTheBox() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);

        drag(canvas, 94, 70, 94, 140, 0);

        SegmentCurve.Cubic cubic = (SegmentCurve.Cubic) firstPath(draft).nodes().getFirst().curve().orElseThrow();
        assertThat(cubic.first().y()).isEqualTo(32);
        assertThat(cubic.second().y()).isEqualTo(32);
    }

    @Test
    void ctrlClickOnTheCurveStraightensIt() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);
        click(canvas, 94, 70, 0);
        CurvePoint middle = firstPath(draft).pointAt(0, 0.5);

        click(canvas, 70 + (int) Math.round(middle.x()), 70 + (int) Math.round(middle.y()),
                InputEvent.CTRL_DOWN_MASK);

        assertThat(firstPath(draft).nodes().getFirst().curve()).isEmpty();
    }

    @Test
    void aCurvedSegmentUnderThePointerShowsItsTwoHandles() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft);
        canvas.setTool(ShapeTool.CURVE);
        click(canvas, 94, 70, 0);
        SegmentCurve.Cubic cubic = (SegmentCurve.Cubic) firstPath(draft).nodes().getFirst().curve().orElseThrow();
        CurvePoint middle = firstPath(draft).pointAt(0, 0.5);

        move(canvas, 70 + (int) Math.round(middle.x()), 70 + (int) Math.round(middle.y()), 0);

        assertThat(canvas.visibleHandles()).containsExactly(cubic.first(), cubic.second());
        SymbolPoint first = cubic.first();
        assertThat(paint(canvas).getRGB(70 + first.x(), 70 + first.y()))
                .isEqualTo(ColorSchemes.LIGHT.selection().getRGB());
    }

    static void drag(ShapeCanvas canvas, int fromX, int fromY, int toX, int toY, int modifiers) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0,
                modifiers | InputEvent.BUTTON1_DOWN_MASK, fromX, fromY, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0,
                modifiers | InputEvent.BUTTON1_DOWN_MASK, toX, toY, 0, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, modifiers, toX, toY, 1, false,
                MouseEvent.BUTTON1));
    }
}
