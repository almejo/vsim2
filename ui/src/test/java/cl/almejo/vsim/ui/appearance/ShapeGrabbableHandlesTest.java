package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.CurvePoint;
import cl.almejo.vsim.core.component.appearance.HandleSide;
import cl.almejo.vsim.core.component.appearance.SegmentCurve;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPathCodec;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.Cursor;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.appearance.ShapeCurveToolTest.drag;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.click;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.firstPath;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.move;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.paint;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.rectangle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The handles of every curved segment are on view and can be grabbed at once with Nodos and with «Curvar tramo»: no
 * hover over the segment and no chosen node first. An arch on the 6 × 4 box at zoom 1, corner at (70, 70), one eighth
 * per pixel: its nodes at (70, 102), (86, 86) and (102, 102) on screen; the middle node's handles at (78, 86) and
 * (94, 86), the end nodes' at (70, 94) and (102, 94).
 */
class ShapeGrabbableHandlesTest {
    private static final String ARCH = "M 0 32 C 0 24 8 16 16 16 C 24 16 32 24 32 32";
    private static final int MIDDLE = 1;
    private static final List<SymbolPoint> ARCH_HANDLES = List.of(new SymbolPoint(0, 24), new SymbolPoint(8, 16),
            new SymbolPoint(24, 16), new SymbolPoint(32, 24));

    @Test
    void nodosShowsTheHandlesOfEveryCurvedSegmentWithoutChoosingANode() {
        ShapeCanvas canvas = canvas(arch(), ShapeTool.NODES);

        assertThat(canvas.visibleHandles()).containsExactlyInAnyOrderElementsOf(ARCH_HANDLES);
        assertThat(paint(canvas).getRGB(78, 86)).isEqualTo(ColorSchemes.LIGHT.selection().getRGB());
        assertThat(paint(canvas).getRGB(94, 86)).isEqualTo(ColorSchemes.LIGHT.selection().getRGB());
    }

    @Test
    void curvarTramoShowsTheHandlesOfEveryCurvedSegmentWithThePointerElsewhere() {
        ShapeCanvas canvas = canvas(arch(), ShapeTool.CURVE);

        move(canvas, 300, 250, 0);

        assertThat(canvas.visibleHandles()).containsExactlyInAnyOrderElementsOf(ARCH_HANDLES);
        assertThat(paint(canvas).getRGB(94, 86)).isEqualTo(ColorSchemes.LIGHT.selection().getRGB());
    }

    @Test
    void nodosGrabsAHandleWithoutChoosingItsNodeFirstAndTheOtherTurnsOpposite() {
        AtomicReference<SymbolDraft> draft = arch();
        ShapeCanvas canvas = canvas(draft, ShapeTool.NODES);

        drag(canvas, 94, 86, 92, 80, 0);

        SymbolPath arch = firstPath(draft);
        assertThat(arch.handle(MIDDLE, HandleSide.OUTGOING)).contains(new SymbolPoint(22, 10));
        assertThat(arch.handle(MIDDLE, HandleSide.INCOMING)).contains(new SymbolPoint(10, 22));
        assertThat(arch.nodes().get(MIDDLE).point()).isEqualTo(new SymbolPoint(16, 16));
    }

    @Test
    void curvarTramoGrabsAHandleBeforeTheCurveUnderIt() {
        AtomicReference<SymbolDraft> draft = arch();
        ShapeCanvas canvas = canvas(draft, ShapeTool.CURVE);

        drag(canvas, 94, 86, 92, 80, 0);

        SymbolPath arch = firstPath(draft);
        assertThat(arch.handle(MIDDLE, HandleSide.OUTGOING)).contains(new SymbolPoint(22, 10));
        assertThat(arch.handle(MIDDLE, HandleSide.INCOMING)).contains(new SymbolPoint(10, 22));
        assertThat(arch.handle(2, HandleSide.INCOMING)).as("the far handle of the segment stays")
                .contains(new SymbolPoint(32, 24));
    }

    @Test
    void altOnAHandleWithCurvarTramoMovesItAloneAndMakesTheNodeACorner() {
        AtomicReference<SymbolDraft> draft = arch();
        ShapeCanvas canvas = canvas(draft, ShapeTool.CURVE);

        drag(canvas, 94, 86, 92, 80, InputEvent.ALT_DOWN_MASK);

        SymbolPath arch = firstPath(draft);
        assertThat(arch.handle(MIDDLE, HandleSide.OUTGOING)).contains(new SymbolPoint(22, 10));
        assertThat(arch.handle(MIDDLE, HandleSide.INCOMING)).contains(new SymbolPoint(8, 16));
        assertThat(draft.get().smooth(0, MIDDLE)).isFalse();
    }

    @Test
    void thePointerOnAHandleOffersToGrabIt() {
        ShapeCanvas canvas = canvas(arch(), ShapeTool.CURVE);

        move(canvas, 94, 86, 0);

        assertThat(canvas.getCursor().getType()).isEqualTo(Cursor.HAND_CURSOR);
        assertThat(canvas.getToolTipText()).isEqualTo(Messages.text("shape.handle.hint"));
    }

    @Test
    void rightAfterCurvingASegmentItsHandlesStayShownAndAreGrabbedAtOnce() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft, ShapeTool.CURVE);
        click(canvas, 94, 70, 0);
        SegmentCurve.Cubic curved = (SegmentCurve.Cubic) firstPath(draft).nodes().getFirst().curve().orElseThrow();
        move(canvas, 300, 250, 0);
        assertThat(canvas.visibleHandles()).containsExactlyInAnyOrder(curved.first(), curved.second());

        drag(canvas, 70 + curved.first().x(), 70 + curved.first().y(), 70 + 12, 70 + 20, 0);

        SegmentCurve.Cubic dragged = (SegmentCurve.Cubic) firstPath(draft).nodes().getFirst().curve().orElseThrow();
        assertThat(dragged.first()).isEqualTo(new SymbolPoint(12, 20));
        assertThat(dragged.second()).isEqualTo(curved.second());
    }

    @Test
    void draggingTheBodyOfACurvedSegmentStillBendsIt() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = canvas(draft, ShapeTool.CURVE);
        click(canvas, 94, 70, 0);
        CurvePoint middle = firstPath(draft).pointAt(0, 0.5);

        drag(canvas, 70 + (int) Math.round(middle.x()), 70 + (int) Math.round(middle.y()), 94, 90, 0);

        CurvePoint bent = firstPath(draft).pointAt(0, 0.5);
        assertThat(bent.x()).isCloseTo(24, within(1.0));
        assertThat(bent.y()).isCloseTo(20, within(1.0));
    }

    @Test
    void agregarNodoOnACubicSplitsItWithoutChangingTheOutlineAndTheNewNodeIsSmooth() {
        AtomicReference<SymbolDraft> draft = arch();
        SymbolPath before = firstPath(draft);
        CurvePoint onCurve = before.pointAt(0, 0.4);
        ShapeCanvas canvas = canvas(draft, ShapeTool.ADD_NODE);

        click(canvas, 70 + (int) Math.round(onCurve.x()), 70 + (int) Math.round(onCurve.y()), 0);

        SymbolPath after = firstPath(draft);
        assertThat(after.nodes()).hasSize(4);
        assertThat(after.hasTwoHandles(1)).isTrue();
        assertThat(draft.get().smooth(0, 1)).isTrue();
        for (int sample = 0; sample <= 10; sample++) {
            for (int half = 0; half < 2; half++) {
                CurvePoint point = after.pointAt(half, sample / 10.0);
                CurvePoint nearest = before.pointAt(0, before.nearestParameter(0, point.x(), point.y()));
                assertThat(Math.hypot(nearest.x() - point.x(), nearest.y() - point.y())).isLessThan(1.0);
            }
        }
    }

    private static AtomicReference<SymbolDraft> arch() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        ComponentAppearance defaults = ComponentAppearance.of(definition);
        SymbolPath path = SymbolPathCodec.decode(ARCH, false, defaults.size());
        ComponentAppearance appearance = new ComponentAppearance(defaults.size(),
                Optional.of(new SymbolDrawing(defaults.size(), List.of(path), true)), defaults.pins());
        return new AtomicReference<>(SymbolDraft.of(appearance, definition.pinout(), false));
    }

    private static ShapeCanvas canvas(AtomicReference<SymbolDraft> draft, ShapeTool tool) {
        ShapeCanvas canvas = ShapeNodeToolsTest.canvas(draft);
        canvas.setSnap(false);
        canvas.setTool(tool);
        return canvas;
    }
}
