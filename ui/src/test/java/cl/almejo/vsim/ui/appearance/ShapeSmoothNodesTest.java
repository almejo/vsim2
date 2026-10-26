package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.DraftHistory;
import cl.almejo.vsim.core.component.appearance.HandleSide;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPathCodec;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.appearance.ShapeCurveToolTest.drag;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.click;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.firstPath;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.paint;
import static cl.almejo.vsim.ui.appearance.ShapeNodeToolsTest.rectangle;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nodos (N) on a node between two curves: choosing it shows its two handles joined by a line through it; dragging one
 * turns the other the opposite way with the same length, Alt drags it alone, a double click toggles smooth / corner.
 * An arch on the 6 × 4 box at zoom 1, corner at (70, 70), one eighth per pixel: its middle node is at (86, 86) on
 * screen, its handles at (78, 86) and (94, 86).
 */
class ShapeSmoothNodesTest {
    private static final String ARCH = "M 0 32 C 0 24 8 16 16 16 C 24 16 32 24 32 32";
    private static final int MIDDLE = 1;

    @Test
    void choosingANodeBetweenTwoCurvesShowsItsTwoHandles() {
        AtomicReference<SymbolDraft> draft = arch();
        ShapeCanvas canvas = nodesCanvas(draft);

        click(canvas, 86, 86, 0);

        assertThat(canvas.visibleHandles()).contains(new SymbolPoint(8, 16), new SymbolPoint(24, 16));
        assertThat(paint(canvas).getRGB(78, 86)).isEqualTo(ColorSchemes.LIGHT.selection().getRGB());
        assertThat(paint(canvas).getRGB(94, 86)).isEqualTo(ColorSchemes.LIGHT.selection().getRGB());
        assertThat(canvas.nodeLabel().orElseThrow()).endsWith(Messages.text("shape.node.smooth"));
    }

    @Test
    void draggingOneHandleTurnsTheOtherOppositeWithTheSameLengthAndTheNodeStays() {
        AtomicReference<SymbolDraft> draft = arch();
        ShapeCanvas canvas = nodesCanvas(draft);
        click(canvas, 86, 86, 0);

        drag(canvas, 94, 86, 92, 80, 0);

        SymbolPath arch = firstPath(draft);
        assertThat(arch.handle(MIDDLE, HandleSide.OUTGOING)).contains(new SymbolPoint(22, 10));
        assertThat(arch.handle(MIDDLE, HandleSide.INCOMING)).contains(new SymbolPoint(10, 22));
        assertThat(arch.nodes().get(MIDDLE).point()).isEqualTo(new SymbolPoint(16, 16));
    }

    @Test
    void altDragsOneHandleAloneWithoutPanningAndTheNodeBecomesACorner() {
        AtomicReference<SymbolDraft> draft = arch();
        ShapeCanvas canvas = nodesCanvas(draft);
        click(canvas, 86, 86, 0);
        Rectangle2D box = canvas.boxOnScreen();

        drag(canvas, 94, 86, 92, 80, InputEvent.ALT_DOWN_MASK);

        SymbolPath arch = firstPath(draft);
        assertThat(arch.handle(MIDDLE, HandleSide.OUTGOING)).contains(new SymbolPoint(22, 10));
        assertThat(arch.handle(MIDDLE, HandleSide.INCOMING)).contains(new SymbolPoint(8, 16));
        assertThat(draft.get().smooth(0, MIDDLE)).isFalse();
        assertThat(canvas.boxOnScreen()).isEqualTo(box);
    }

    @Test
    void aDoubleClickOnTheNodeTogglesSmoothAndCorner() {
        AtomicReference<SymbolDraft> draft = arch();
        ShapeCanvas canvas = nodesCanvas(draft);

        click(canvas, 86, 86, 0, 2);
        assertThat(draft.get().smooth(0, MIDDLE)).isFalse();
        assertThat(canvas.nodeLabel().orElseThrow()).endsWith(Messages.text("shape.node.corner"));

        click(canvas, 86, 86, 0, 2);
        assertThat(draft.get().smooth(0, MIDDLE)).isTrue();
    }

    @Test
    void aDoubleClickOnANodeWithoutTwoCurvesSaysWhyAndChangesNothing() {
        AtomicReference<SymbolDraft> draft = rectangle();
        ShapeCanvas canvas = nodesCanvas(draft);
        SymbolPath before = firstPath(draft);

        click(canvas, 118, 70, 0, 2);

        assertThat(canvas.refusal()).hasValue(Messages.text("shape.node.smoothNeedsCurves"));
        assertThat(firstPath(draft)).isEqualTo(before);
    }

    @Test
    void aHandleDragIsOneUndoStep() {
        DraftHistory history = new DraftHistory(arch().get());
        AtomicReference<SymbolDraft> shown = new AtomicReference<>(history.current());
        ShapeCanvas canvas = new ShapeCanvas(ShapeEditorPanelTest.definition(), shown::get, published -> {
            history.update(published);
            shown.set(history.current());
        }, shown::set, () -> ColorSchemes.LIGHT, () -> { });
        canvas.setZoom(1);
        canvas.setSize(400, 300);
        canvas.setSnap(false);
        canvas.setTool(ShapeTool.NODES);
        click(canvas, 86, 86, 0);

        drag(canvas, 94, 86, 92, 80, 0);
        history.undo();

        SymbolPath arch = (SymbolPath) history.current().elements().getFirst();
        assertThat(arch.handle(MIDDLE, HandleSide.OUTGOING)).contains(new SymbolPoint(24, 16));
        assertThat(arch.handle(MIDDLE, HandleSide.INCOMING)).contains(new SymbolPoint(8, 16));
    }

    private static AtomicReference<SymbolDraft> arch() {
        ComponentDefinition definition = ShapeEditorPanelTest.definition();
        ComponentAppearance defaults = ComponentAppearance.of(definition);
        SymbolPath path = SymbolPathCodec.decode(ARCH, false, defaults.size());
        ComponentAppearance appearance = new ComponentAppearance(defaults.size(),
                Optional.of(new SymbolDrawing(defaults.size(), List.of(path), true)), defaults.pins());
        return new AtomicReference<>(SymbolDraft.of(appearance, definition.pinout(), false));
    }

    private static ShapeCanvas nodesCanvas(AtomicReference<SymbolDraft> draft) {
        ShapeCanvas canvas = ShapeNodeToolsTest.canvas(draft);
        canvas.setSnap(false);
        canvas.setTool(ShapeTool.NODES);
        return canvas;
    }
}
