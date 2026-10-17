package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import cl.almejo.vsim.ui.wiring.SelectionMoves;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The frame gestures through the select tool (spec §2 table, §3, §7): a frame at (0, 0), 20 x 12, title bar y 0..2,
 * with a NOT gate (4 x 4) at (4, 4) inside.
 */
class FrameGesturesTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private final FrameGestures gestures = new FrameGestures();

    FrameGesturesTest() {
        tool.addDelegate(gestures);
        // what FramesFeatures registers: operations on a selected locked frame act on its block
        context.selection.addExpansion(FramesFeatures.lockedBlocks(context.openDocument()));
    }

    @AfterEach
    void close() {
        context.close();
    }

    private Frame frame(int x, int y, int width, int height, boolean locked) {
        Frame frame = new Frame(context.openDocument().document().newFrameId(), new GridPoint(x, y), width, height,
                "Sumador", FrameTint.BLUE, locked);
        context.openDocument().execute(new CreateFrameCommand(frame));
        return frame;
    }

    private Component gate(int x, int y) {
        return place(context.openDocument(), StandardComponentTypes.NOT, x, y, Map.of());
    }

    private Frame current(Frame frame) {
        return context.openDocument().document().frame(frame.id()).orElseThrow();
    }

    private Component current(Component component) {
        return context.openDocument().document().component(component.id()).orElseThrow();
    }

    // --- spec §2: clicks on the title bar

    @Test
    void aClickOnAnUnlockedTitleSelectsOnlyTheFrame() {
        Frame frame = frame(0, 0, 20, 12, false);
        gate(4, 4);
        context.click(tool, 10, 1);
        assertThat(context.selection.ids()).containsExactly(frame.id());
    }

    @Test
    void aClickOnALockedTitleSelectsOnlyTheFrame() {
        Frame frame = frame(0, 0, 20, 12, true);
        Component inside = gate(4, 4);
        context.click(tool, 10, 1);
        assertThat(context.selection.ids()).containsExactly(frame.id());
        assertThat(context.selection.effectiveIds()).containsExactlyInAnyOrder(frame.id(), inside.id());
    }

    @Test
    void shiftClickOnATitleTogglesOnlyTheFrame() {
        Frame unlocked = frame(0, 0, 20, 12, false);
        Frame locked = frame(30, 0, 20, 12, true);
        gate(34, 4);
        context.clickWith(tool, 10, 1, true, false);
        context.clickWith(tool, 40, 1, true, false);
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(unlocked.id(), locked.id());
        context.clickWith(tool, 40, 1, true, false);
        assertThat(context.selection.ids()).containsExactly(unlocked.id());
    }

    // --- spec §2: dragging the title bar

    @Test
    void draggingAnUnlockedTitleMovesOnlyTheFrameAsOneUndoStep() {
        Frame frame = frame(0, 0, 20, 12, false);
        Component inside = gate(4, 4);
        context.drag(tool, 10, 1, 13, 3);
        assertThat(current(frame).topLeft()).isEqualTo(new GridPoint(3, 2));
        assertThat(current(inside)).isEqualTo(inside);
        assertThat(context.openDocument().history().undoDescription()).contains("command.move");
        context.openDocument().history().undo();
        assertThat(current(frame)).isEqualTo(frame);
        assertThat(context.openDocument().document().elements()).hasSize(2);
    }

    @Test
    void draggingALockedTitleMovesTheFrameAndItsContentsAsOneUndoStep() {
        Frame frame = frame(0, 0, 20, 12, true);
        Component inside = gate(4, 4);
        Component outside = gate(30, 4);
        context.drag(tool, 10, 1, 13, 3);
        assertThat(context.selection.ids()).containsExactly(frame.id());
        assertThat(current(frame).topLeft()).isEqualTo(new GridPoint(3, 2));
        assertThat(current(inside).position()).isEqualTo(new GridPoint(7, 6));
        assertThat(current(outside)).isEqualTo(outside);
        context.openDocument().history().undo();
        assertThat(current(frame)).isEqualTo(frame);
        assertThat(current(inside)).isEqualTo(inside);
    }

    @Test
    void aNestedLockedFrameAndItsContentsMoveExactlyOnce() {
        Frame outer = frame(0, 0, 40, 30, true);
        Frame inner = frame(5, 5, 20, 15, true);
        Component nested = gate(8, 9);
        context.drag(tool, 30, 1, 35, 2);
        assertThat(current(outer).topLeft()).isEqualTo(new GridPoint(5, 1));
        assertThat(current(inner).topLeft()).isEqualTo(new GridPoint(10, 6));
        assertThat(current(nested).position()).isEqualTo(new GridPoint(13, 10));
        context.openDocument().history().undo();
        assertThat(current(outer)).isEqualTo(outer);
        assertThat(current(inner)).isEqualTo(inner);
        assertThat(current(nested)).isEqualTo(nested);
    }

    @Test
    void draggingTheTitleOfAFrameInAMultiSelectionMovesTheWholeSelection() {
        Frame frame = frame(0, 0, 20, 12, false);
        Component outside = gate(30, 4);
        context.selection.set(List.of(frame.id(), outside.id()));
        context.drag(tool, 10, 1, 12, 1);
        assertThat(current(frame).topLeft()).isEqualTo(new GridPoint(2, 0));
        assertThat(current(outside).position()).isEqualTo(new GridPoint(32, 4));
        context.click(tool, 12, 1);
        assertThat(context.selection.ids()).containsExactly(frame.id());
    }

    @Test
    void draggingAnElementOfASelectionThatHoldsALockedFrameMovesTheBlockToo() {
        Frame frame = frame(0, 0, 20, 12, true);
        Component inside = gate(4, 4);
        Component outside = gate(30, 4);
        context.selection.set(List.of(frame.id(), outside.id()));
        context.drag(tool, 32, 6, 34, 7);
        assertThat(current(outside).position()).isEqualTo(new GridPoint(32, 5));
        assertThat(current(frame).topLeft()).isEqualTo(new GridPoint(2, 1));
        assertThat(current(inside).position()).isEqualTo(new GridPoint(6, 5));
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(frame.id(), outside.id());
    }

    @Test
    void controlDraggingASelectedLockedFrameCopiesItsContentsToo() {
        Frame frame = frame(0, 0, 20, 12, true);
        Component outside = gate(30, 4);
        gate(4, 4);
        context.selection.set(List.of(frame.id(), outside.id()));
        tool.mousePressed(context, TestToolContext.at(32, 6));
        tool.mouseDragged(context, TestToolContext.at(32, 26));
        tool.mouseReleased(context, TestToolContext.withModifiers(32, 26, false, true));
        assertThat(context.openDocument().document().frames()).hasSize(2);
        assertThat(context.openDocument().document().components()).hasSize(4);
    }

    @Test
    void controlDraggingASelectedUnlockedFrameCopiesTheFrameAlone() {
        Frame frame = frame(0, 0, 20, 12, false);
        Component outside = gate(30, 4);
        gate(4, 4);
        context.selection.set(List.of(frame.id(), outside.id()));
        tool.mousePressed(context, TestToolContext.at(32, 6));
        tool.mouseDragged(context, TestToolContext.at(32, 26));
        tool.mouseReleased(context, TestToolContext.withModifiers(32, 26, false, true));
        assertThat(context.openDocument().document().frames()).hasSize(2);
        assertThat(context.openDocument().document().components()).hasSize(3);
    }

    @Test
    void theGhostsOfALockedFrameDragShowItsContents() {
        Frame frame = frame(0, 0, 20, 12, true);
        Component inside = gate(4, 4);
        context.click(tool, 10, 1);
        tool.mousePressed(context, TestToolContext.at(10, 1));
        tool.mouseDragged(context, TestToolContext.at(13, 3));
        context.ghosts.clear();
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).contains(frame.translate(3, 2), inside.translate(3, 2));
        tool.mouseReleased(context, TestToolContext.at(13, 3));
    }

    @Test
    void escapeCancelsAFrameDrag() {
        Frame frame = frame(0, 0, 20, 12, false);
        tool.mousePressed(context, TestToolContext.at(10, 1));
        tool.mouseDragged(context, TestToolContext.at(15, 5));
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        tool.mouseReleased(context, TestToolContext.at(15, 5));
        assertThat(current(frame)).isEqualTo(frame);
        assertThat(gestures.veil()).isEmpty();
    }

    // --- spec §2: resizing

    @Test
    void draggingACornerOfAnUnlockedFrameResizesItAsOneUndoStep() {
        Frame frame = frame(0, 0, 20, 12, false);
        Component inside = gate(4, 4);
        context.drag(tool, 20, 12, 25, 15);
        assertThat(current(frame)).isEqualTo(frame.withRectangle(new GridPoint(0, 0), 25, 15));
        assertThat(current(inside)).isEqualTo(inside);
        assertThat(context.openDocument().history().undoDescription()).contains("command.resizeFrame");
        context.openDocument().history().undo();
        assertThat(current(frame)).isEqualTo(frame);
    }

    @Test
    void eachEdgeMovesOnlyItsSide() {
        Frame frame = frame(0, 0, 20, 12, false);
        context.drag(tool, 0, 6, -3, 6); // west
        context.drag(tool, 10, 0, 10, -2); // north
        context.drag(tool, 20, 7, 22, 7); // east
        context.drag(tool, 10, 12, 10, 13); // south
        assertThat(current(frame)).isEqualTo(frame.withRectangle(new GridPoint(-3, -2), 25, 15));
    }

    @Test
    void aResizeNeverGoesUnderTheMinimumSize() {
        Frame frame = frame(0, 0, 20, 12, false);
        context.drag(tool, 0, 6, 40, 6);
        context.drag(tool, 17, 12, 17, -30);
        Frame resized = current(frame);
        assertThat(resized.width()).isEqualTo(Frame.MINIMUM_WIDTH);
        assertThat(resized.topLeft().x()).isEqualTo(20 - Frame.MINIMUM_WIDTH);
        assertThat(resized.height()).isEqualTo(Frame.MINIMUM_HEIGHT);
        assertThat(resized.topLeft().y()).isZero();
    }

    @Test
    void aLockedFrameCannotBeResized() {
        Frame frame = frame(0, 0, 20, 12, true);
        context.drag(tool, 20, 6, 25, 6);
        context.drag(tool, 20, 12, 25, 15);
        assertThat(current(frame)).isEqualTo(frame);
        assertThat(context.openDocument().history().undoDescription()).contains("command.createFrame");
    }

    // --- spec §2: the interior belongs to the canvas

    @Test
    void aPressOnTheEmptyInteriorStartsARubberBandThatSelectsWhatIsInside() {
        frame(0, 0, 20, 12, false);
        Component inside = gate(4, 4);
        context.drag(tool, 3, 3, 10, 10);
        assertThat(context.selection.ids()).containsExactly(inside.id());
        context.click(tool, 15, 9);
        assertThat(context.selection.isEmpty()).isTrue();
    }

    @Test
    void aClickOnAComponentInsideAFrameSelectsTheComponent() {
        frame(0, 0, 20, 12, true);
        Component inside = gate(4, 4);
        context.click(tool, 6, 6);
        assertThat(context.selection.ids()).containsExactly(inside.id());
    }

    @Test
    void aRubberBandSelectsAFrameOnlyWhenItEnclosesIt() {
        Frame frame = frame(0, 0, 20, 12, false);
        Component inside = gate(4, 4);
        context.drag(tool, -1, 30, 15, 3);
        assertThat(context.selection.ids()).containsExactly(inside.id());
        context.drag(tool, -1, 30, 21, -1);
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(frame.id(), inside.id());
    }

    @Test
    void selectAllIncludesFrames() {
        Frame frame = frame(0, 0, 20, 12, false);
        Component inside = gate(4, 4);
        context.selection.selectAll(context.openDocument().document());
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(frame.id(), inside.id());
    }

    // --- spec §3: the veil

    @Test
    void theVeilShowsOnlyWhileAFrameGestureDrags() {
        Frame frame = frame(0, 0, 20, 12, false);
        gate(4, 4);
        assertThat(gestures.veil()).isEmpty();
        tool.mousePressed(context, TestToolContext.at(10, 1));
        assertThat(gestures.veil()).isEmpty();
        tool.mouseDragged(context, TestToolContext.at(13, 1));
        assertThat(gestures.veil()).contains(FramePainter.bounds(frame.translate(3, 0)));
        tool.mouseReleased(context, TestToolContext.at(13, 1));
        assertThat(gestures.veil()).isEmpty();

        tool.mousePressed(context, TestToolContext.at(23, 12));
        tool.mouseDragged(context, TestToolContext.at(25, 14));
        assertThat(gestures.veil()).contains(new Rectangle2D.Double(3 * 8, 0, 22 * 8, 14 * 8));
        tool.mouseReleased(context, TestToolContext.at(25, 14));
        assertThat(gestures.veil()).isEmpty();

        // moving a component is not a frame gesture
        tool.mousePressed(context, TestToolContext.at(6, 6));
        tool.mouseDragged(context, TestToolContext.at(9, 6));
        assertThat(gestures.veil()).isEmpty();
        tool.mouseReleased(context, TestToolContext.at(9, 6));
    }

    @Test
    void theVeilDarkensOutsideTheFrameAndLeavesTheInsideClear() {
        frame(2, 2, 10, 8, true);
        tool.mousePressed(context, TestToolContext.at(6, 3));
        tool.mouseDragged(context, TestToolContext.at(6, 3.9));
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setClip(0, 0, 200, 200);
        tool.paintOverlay(graphics, context);
        graphics.dispose();
        // the frame, moved one unit down, covers (16, 24)..(96, 88) in document pixels
        int veilAlpha = ColorScheme.LIGHT.frameVeil().getAlpha();
        assertThat(image.getRGB(4, 4) >>> 24).isEqualTo(veilAlpha);
        assertThat(image.getRGB(150, 150) >>> 24).isEqualTo(veilAlpha);
        assertThat(image.getRGB(60, 60) >>> 24).isZero();
    }

    // --- spec §7: cursors and handles

    @Test
    void theCursorSaysWhatAPressOnEachPartDoes() {
        frame(0, 0, 20, 12, false);
        frame(30, 0, 20, 12, true);
        Map<double[], CanvasCursor> expected = Map.ofEntries(
                Map.entry(new double[]{10, 1}, CanvasCursor.MOVE),
                Map.entry(new double[]{40, 1}, CanvasCursor.MOVE),
                Map.entry(new double[]{10, 0}, CanvasCursor.RESIZE_NORTH),
                Map.entry(new double[]{10, 12.3}, CanvasCursor.RESIZE_SOUTH),
                Map.entry(new double[]{19.8, 6}, CanvasCursor.RESIZE_EAST),
                Map.entry(new double[]{0.2, 6}, CanvasCursor.RESIZE_WEST),
                Map.entry(new double[]{0, 0}, CanvasCursor.RESIZE_NORTH_WEST),
                Map.entry(new double[]{20, 0}, CanvasCursor.RESIZE_NORTH_EAST),
                Map.entry(new double[]{0, 12}, CanvasCursor.RESIZE_SOUTH_WEST),
                Map.entry(new double[]{20, 12}, CanvasCursor.RESIZE_SOUTH_EAST),
                Map.entry(new double[]{10, 8}, CanvasCursor.DEFAULT),
                Map.entry(new double[]{50, 6}, CanvasCursor.DEFAULT),
                Map.entry(new double[]{40, 12}, CanvasCursor.DEFAULT));
        expected.forEach((point, cursor) -> {
            tool.mouseMoved(context, TestToolContext.at(25, 25)); // the arrow
            tool.mouseMoved(context, TestToolContext.at(10, 1)); // the move cursor, so every next one is set
            tool.mouseMoved(context, TestToolContext.at(point[0], point[1]));
            assertThat(context.lastCursor()).as("at %s, %s", point[0], point[1]).isEqualTo(cursor);
        });
    }

    @Test
    void handlesShowOnAnUnlockedFrameWhileHoveredOrSelectedAndNeverOnALockedOne() {
        Frame unlocked = frame(0, 0, 20, 12, false);
        Frame locked = frame(30, 0, 20, 12, true);
        assertThat(gestures.framesWithHandles(context)).isEmpty();
        tool.mouseMoved(context, TestToolContext.at(10, 8));
        assertThat(gestures.framesWithHandles(context)).containsExactly(unlocked);
        tool.mouseMoved(context, TestToolContext.at(40, 8));
        assertThat(gestures.framesWithHandles(context)).isEmpty();
        context.selection.set(Set.of(unlocked.id(), locked.id()));
        assertThat(gestures.framesWithHandles(context)).containsExactly(unlocked);
        tool.mouseExited(context);
        context.selection.clear();
        assertThat(gestures.framesWithHandles(context)).isEmpty();
    }

    @Test
    void theEightHandlesSitOnTheCornersAndTheMiddleOfEachEdge() {
        Frame frame = new Frame(context.openDocument().document().newFrameId(), GridPoint.ORIGIN, 10, 6, "", null,
                false);
        List<Rectangle2D> handles = FrameGestures.handles(frame, 4);
        assertThat(handles).hasSize(8);
        assertThat(handles).contains(new Rectangle2D.Double(-2, -2, 4, 4), new Rectangle2D.Double(78, 46, 4, 4),
                new Rectangle2D.Double(38, -2, 4, 4), new Rectangle2D.Double(-2, 22, 4, 4));
    }

    // --- the context menu

    @Test
    void aRightClickOnALockedTitleSelectsTheFrameAndOpensTheMenuForIt() {
        Frame frame = frame(0, 0, 20, 12, true);
        gate(4, 4);
        tool.mousePressed(context, TestToolContext.event(10, 1, CanvasMouseEvent.SECONDARY, 1, false));
        assertThat(context.selection.ids()).containsExactly(frame.id());
        assertThat(context.contextMenuTarget).contains(frame);
    }

    @Test
    void aDoubleClickOnTheTitleBarSelectsTheFrameAndOpensItsTitleEditor() {
        Frame frame = frame(0, 0, 20, 12, false);
        context.click(tool, 10, 1);
        tool.mousePressed(context, TestToolContext.event(10, 1, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(10, 1, CanvasMouseEvent.PRIMARY, 2, false));
        assertThat(context.editedLabel).isEqualTo(current(frame));
        assertThat(context.selection.ids()).containsExactly(frame.id());
    }

    @Test
    void aDoubleClickOnALockedTitleSelectsTheFrameAloneAndOpensItsTitleEditor() {
        Frame frame = frame(0, 0, 20, 12, true);
        gate(4, 4);
        context.click(tool, 10, 1);
        tool.mousePressed(context, TestToolContext.event(10, 1, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(10, 1, CanvasMouseEvent.PRIMARY, 2, false));
        assertThat(context.editedLabel).isEqualTo(current(frame));
        assertThat(context.selection.ids()).containsExactly(frame.id());
    }

    @Test
    void aDoubleClickOnTheEmptyInteriorOpensNoEditor() {
        frame(0, 0, 20, 12, false);
        context.click(tool, 10, 8);
        tool.mousePressed(context, TestToolContext.event(10, 8, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(10, 8, CanvasMouseEvent.PRIMARY, 2, false));
        assertThat(context.editedLabel).isNull();
    }

    @Test
    void afterADoubleClickOnATitleWhoseReleaseNeverArrivesAClickOnAnotherElementSelectsIt() {
        // the title editor is modal, so the release of the double click is lost
        frame(0, 0, 20, 12, false);
        Component outside = gate(30, 4);
        context.click(tool, 10, 1);
        tool.mousePressed(context, TestToolContext.event(10, 1, CanvasMouseEvent.PRIMARY, 2, false));
        context.click(tool, 32, 6);
        assertThat(context.selection.ids()).containsExactly(outside.id());
        assertThat(tool.autoscrolls()).isFalse();
    }

    @Test
    void aPressOnThePadlockIsHandledWithoutGrabbingSoTheCursorStaysAndTheNextPressIsTheTools() {
        Frame frame = frame(0, 0, 20, 12, false);
        Component outside = gate(30, 4);
        tool.mouseMoved(context, TestToolContext.at(25, 25));
        int cursorsBefore = context.cursors.size();
        tool.mousePressed(context, TestToolContext.at(17.25, 1));
        assertThat(current(frame).locked()).isTrue();
        assertThat(context.cursors.subList(cursorsBefore, context.cursors.size())).doesNotContain(CanvasCursor.MOVE);
        context.click(tool, 32, 6);
        assertThat(context.selection.ids()).containsExactly(outside.id());
    }

    @Test
    void aLockedBlockCarriesTheWiresFullyInsideAndLeavesTheOnesCrossingTheBorder() {
        frame(0, 0, 20, 12, true);
        Wire inside = wire(2, 10, 12, 10);
        Wire crossing = wire(15, 6, 30, 6);
        context.drag(tool, 10, 1, 13, 3);
        assertThat(context.openDocument().document().wire(inside.id()).orElseThrow())
                .isEqualTo(inside.translate(3, 2));
        assertThat(context.openDocument().document().wire(crossing.id()).orElseThrow()).isEqualTo(crossing);
    }

    @Test
    void aLockedBlockDraggedMovesTheWiresInsideAndStretchesTheOnesCrossingItsBorder() {
        frame(0, 0, 20, 12, true);
        gate(4, 4);
        place(context.openDocument(), StandardComponentTypes.LED, 29, 3, Map.of());
        Wire inside = wire(2, 6, 4, 6);
        Wire crossing = wire(8, 6, 30, 6);
        tool.mousePressed(context, TestToolContext.at(10, 1));
        tool.mouseDragged(context, TestToolContext.at(10, 4));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        assertThat(context.ghosts).contains(inside.translate(0, 3));
        tool.mouseReleased(context, TestToolContext.at(10, 4));

        assertThat(context.openDocument().document().wire(inside.id()).orElseThrow())
                .isEqualTo(inside.translate(0, 3));
        assertThat(context.openDocument().document().wires()).extracting(Wire::start, Wire::end)
                .containsExactlyInAnyOrder(
                        tuple(new GridPoint(2, 9), new GridPoint(4, 9)),
                        tuple(new GridPoint(8, 6), new GridPoint(30, 6)),
                        tuple(new GridPoint(8, 6), new GridPoint(8, 9)));
        assertThat(context.openDocument().document().wire(crossing.id())).isPresent();
        context.openDocument().history().undo();
        assertThat(context.openDocument().document().wires()).containsExactly(inside, crossing);
    }

    @Test
    void shiftWhileDraggingALockedBlockLeavesTheCrossingWiresBehind() {
        frame(0, 0, 20, 12, true);
        gate(4, 4);
        place(context.openDocument(), StandardComponentTypes.LED, 29, 3, Map.of());
        Wire crossing = wire(8, 6, 30, 6);
        context.dragHolding(tool, 10, 1, 10, 4, true, false);
        assertThat(context.openDocument().document().wires()).containsExactly(crossing);
    }

    @Test
    void shiftHeldFromThePressMovesTheLockedBlockAndLeavesTheCrossingWiresBehind() {
        Frame frame = frame(0, 0, 20, 12, true);
        Component inside = gate(4, 4);
        place(context.openDocument(), StandardComponentTypes.LED, 29, 3, Map.of());
        Wire crossing = wire(8, 6, 30, 6);
        tool.mousePressed(context, TestToolContext.withKeys(10, 1, true, false, false));
        tool.mouseDragged(context, TestToolContext.withKeys(10, 4, true, false, false));
        tool.mouseReleased(context, TestToolContext.withKeys(10, 4, true, false, false));
        assertThat(current(frame).topLeft()).isEqualTo(new GridPoint(0, 3));
        assertThat(context.openDocument().document().component(inside.id()).orElseThrow().position())
                .isEqualTo(new GridPoint(4, 7));
        assertThat(context.openDocument().document().wires()).containsExactly(crossing);
    }

    // --- the border of a locked frame is a wall for its contents (2026-10-16)

    @Test
    void aGateInsideALockedFrameStopsAtItsBorderInOneUndoStep() {
        frame(0, 0, 20, 12, true);
        Component inside = gate(4, 4);
        context.drag(tool, 6, 6, 40, 30);
        assertThat(current(inside).position()).isEqualTo(new GridPoint(16, 8));
        context.openDocument().history().undo();
        assertThat(current(inside)).isEqualTo(inside);
    }

    @Test
    void theArrowsStopAtTheBorderToo() {
        frame(0, 0, 20, 12, true);
        Component inside = gate(16, 4);
        SelectionMoves.detached().nudge(context.openDocument(), Set.of(inside.id()), 1, 0);
        assertThat(current(inside)).isEqualTo(inside);
        assertThat(context.openDocument().history().undoDescription()).isNotEqualTo(java.util.Optional.of("command.move"));
        SelectionMoves.detached().nudge(context.openDocument(), Set.of(inside.id()), -5, 0);
        assertThat(current(inside).position()).isEqualTo(new GridPoint(11, 4));
    }

    @Test
    void aShiftDragOfASelectedFrameStoppedByAWallKeepsItSelected() {
        frame(0, 0, 40, 30, true);
        Frame inner = frame(0, 10, 12, 8, false);
        context.click(tool, 6, 11);
        assertThat(context.selection.ids()).containsExactly(inner.id());
        tool.mousePressed(context, TestToolContext.withKeys(6, 11, true, false, false));
        tool.mouseDragged(context, TestToolContext.withKeys(1, 11, true, false, false));
        tool.mouseReleased(context, TestToolContext.withKeys(1, 11, true, false, false));
        assertThat(current(inner)).isEqualTo(inner);
        assertThat(context.selection.ids()).containsExactly(inner.id());
    }

    @Test
    void anUnlockedFrameIsNoWall() {
        frame(0, 0, 20, 12, false);
        Component inside = gate(4, 4);
        context.drag(tool, 6, 6, 40, 6);
        assertThat(current(inside).position()).isEqualTo(new GridPoint(38, 4));
    }

    @Test
    void somethingFromOutsideMayEnterALockedFrame() {
        frame(0, 0, 20, 12, true);
        Component outside = gate(30, 4);
        context.drag(tool, 32, 6, 10, 6);
        assertThat(current(outside).position()).isEqualTo(new GridPoint(8, 4));
    }

    @Test
    void aControlDragCopyIsNotLimited() {
        frame(0, 0, 20, 12, true);
        Component inside = gate(4, 4);
        tool.mousePressed(context, TestToolContext.at(6, 6));
        tool.mouseDragged(context, TestToolContext.withKeys(40, 6, false, true, false));
        tool.mouseReleased(context, TestToolContext.withKeys(40, 6, false, true, false));
        assertThat(current(inside)).isEqualTo(inside);
        assertThat(context.openDocument().document().components()).extracting(Component::position)
                .contains(new GridPoint(38, 4));
    }

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(context.openDocument().document().newWireId(), new GridPoint(startX, startY),
                new GridPoint(endX, endY));
        context.openDocument().execute(new AddWireCommand(wire));
        return wire;
    }

    @Test
    void withTheGesturesInstalledAndNoFramesClicksDragsAndRubberBandsAreUnchanged() {
        try (TestToolContext plainContext = TestToolContext.withNewDocument()) {
            SelectTool plainTool = new SelectTool();
            List<TestToolContext> contexts = List.of(context, plainContext);
            List<SelectTool> tools = List.of(tool, plainTool);
            for (int index = 0; index < 2; index++) {
                TestToolContext each = contexts.get(index);
                SelectTool eachTool = tools.get(index);
                place(each.openDocument(), StandardComponentTypes.NOT, 4, 4, Map.of());
                place(each.openDocument(), StandardComponentTypes.NOT, 20, 4, Map.of());
                each.click(eachTool, 6, 6);
                each.drag(eachTool, 6, 6, 9, 8);
                each.drag(eachTool, 1, 1, 30, 30);
                each.drag(eachTool, 9, 8, 9, 12);
                each.click(eachTool, 40, 40);
            }
            assertThat(context.openDocument().document().elements())
                    .isEqualTo(plainContext.openDocument().document().elements());
            assertThat(context.cursors).isEqualTo(plainContext.cursors);
            assertThat(context.selection.ids()).isEqualTo(plainContext.selection.ids());
        }
    }
}
