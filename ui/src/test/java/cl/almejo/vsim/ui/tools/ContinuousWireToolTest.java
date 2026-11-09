package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** The Wire tool in its continuous mode: each click fixes a corner, a click on a pin or a wire ends. */
class ContinuousWireToolTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final AtomicReference<WireMode> mode = new AtomicReference<>(WireMode.CONTINUOUS);
    private final WireTool tool = new WireTool(mode::get);

    {
        context.tool = tool;
    }

    @AfterEach
    void close() {
        context.close();
    }

    private List<Wire> wires() {
        return context.openDocument().document().wires();
    }

    private void click(double gridX, double gridY) {
        click(gridX, gridY, 1);
    }

    private void click(double gridX, double gridY, int clickCount) {
        tool.mousePressed(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, clickCount, false));
        tool.mouseReleased(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, clickCount, false));
    }

    private void doubleClick(double gridX, double gridY) {
        click(gridX, gridY, 1);
        click(gridX, gridY, 2);
    }

    private void move(double gridX, double gridY) {
        tool.mouseMoved(context, TestToolContext.at(gridX, gridY));
    }

    private GridPoint pin(Component component, int pinIndex) {
        return context.openDocument().registry().typeOf(component).pinLocation(component, pinIndex);
    }

    private Component buffer(int x, int y) {
        return place(context.openDocument(), StandardComponentTypes.BUFFER, x, y,
                Map.of(StandardComponentTypes.NEGATE, true));
    }

    @Test
    void pinToPinInTwoClicksIsOneUndoStepAndTheToolStaysActive() {
        Component first = buffer(0, 0);
        Component second = buffer(20, 6);
        GridPoint from = pin(first, 1);
        GridPoint to = pin(second, 0);
        int componentsBefore = context.openDocument().document().components().size();

        click(from.x(), from.y());
        click(to.x(), to.y());

        assertThat(tool.holdsGesture()).isFalse();
        assertThat(wires()).isNotEmpty();
        assertThat(context.tool).isSameAs(tool);
        assertThat(context.openDocument().history().undoDescription()).contains("command.wire");
        context.openDocument().history().undo();
        assertThat(wires()).isEmpty();
        assertThat(context.openDocument().document().components()).hasSize(componentsBefore);
    }

    @Test
    void cornersInEmptySpaceEndWithADoubleClickAsOneUndoStep() {
        click(0, 0);
        click(4, 2);
        doubleClick(9, 2);

        assertThat(tool.holdsGesture()).isFalse();
        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(new GridPoint(0, 0), new GridPoint(4, 0)),
                tuple(new GridPoint(4, 0), new GridPoint(4, 2)),
                tuple(new GridPoint(4, 2), new GridPoint(9, 2)));
        context.openDocument().history().undo();
        assertThat(wires()).isEmpty();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void aDoubleClickLeavesNoZeroLengthWireAndCommitsOnce() {
        click(0, 0);
        doubleClick(4, 0);

        assertThat(wires()).extracting(Wire::start, Wire::end)
                .containsExactly(tuple(new GridPoint(0, 0), new GridPoint(4, 0)));
        context.openDocument().history().undo();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void theSecondPressOfADoubleClickThatEndedOnAWireStartsNoNewPath() {
        addWire(context.openDocument(), 10, -5, 10, 5);
        click(0, 0);
        doubleClick(10, 0);

        assertThat(tool.holdsGesture()).isFalse();
        assertThat(wires()).hasSize(2);
    }

    @Test
    void aPressAndReleaseThatMovedLessThanTheThresholdIsAClickAtThePress() {
        click(0, 0);
        tool.mousePressed(context, TestToolContext.at(4.4, 0));
        tool.mouseDragged(context, TestToolContext.at(4.6, 0));
        tool.mouseReleased(context, TestToolContext.at(4.6, 0));
        doubleClick(4, 4);

        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(new GridPoint(0, 0), new GridPoint(4, 0)),
                tuple(new GridPoint(4, 0), new GridPoint(4, 4)));
    }

    @Test
    void aRightClickEndsThePathWhereItIs() {
        click(0, 0);
        tool.mousePressed(context, TestToolContext.event(4, 0, CanvasMouseEvent.SECONDARY, 1, false));
        tool.mouseReleased(context, TestToolContext.event(4, 0, CanvasMouseEvent.SECONDARY, 1, false));

        assertThat(wires()).extracting(Wire::start, Wire::end)
                .containsExactly(tuple(new GridPoint(0, 0), new GridPoint(4, 0)));
        assertThat(context.tool).isSameAs(tool);
    }

    @Test
    void aRightClickWithNoPathLeavesTheTool() {
        tool.mousePressed(context, TestToolContext.event(4, 0, CanvasMouseEvent.SECONDARY, 1, false));
        assertThat(context.tool).isSameAs(context.defaultTool);
    }

    @Test
    void backspaceDropsTheLastCornerThenCancelsThenIsNotUsed() {
        click(0, 0);
        click(4, 0);
        move(4, 4);

        assertThat(tool.keyPressed(context, KeyEvent.VK_BACK_SPACE, false)).isTrue();
        assertThat(tool.holdsGesture()).isTrue();
        assertThat(tool.keyPressed(context, KeyEvent.VK_BACK_SPACE, false)).isTrue();
        assertThat(tool.holdsGesture()).isFalse();
        assertThat(tool.keyPressed(context, KeyEvent.VK_BACK_SPACE, false)).isFalse();
        assertThat(wires()).isEmpty();
    }

    @Test
    void droppingTheLastCornerLetsTheNextClickGoOnFromTheOneBefore() {
        click(0, 0);
        click(4, 0);
        tool.keyPressed(context, KeyEvent.VK_BACK_SPACE, false);
        doubleClick(0, 3);

        assertThat(wires()).extracting(Wire::start, Wire::end)
                .containsExactly(tuple(new GridPoint(0, 0), new GridPoint(0, 3)));
    }

    @Test
    void dropLastStepDropsACornerLikeBackspaceAndIsUnusedWithoutAPath() {
        assertThat(tool.dropLastStep(context)).isFalse();
        click(0, 0);
        click(4, 0);
        assertThat(tool.dropLastStep(context)).isTrue();
        assertThat(tool.dropLastStep(context)).isTrue();
        assertThat(tool.holdsGesture()).isFalse();
        assertThat(tool.dropLastStep(context)).isFalse();
    }

    @Test
    void escapeCancelsThePathThenLeavesTheTool() {
        click(0, 0);
        click(4, 0);
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(tool.holdsGesture()).isFalse();
        assertThat(context.tool).isSameAs(tool);
        assertThat(wires()).isEmpty();
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(context.tool).isSameAs(context.defaultTool);
    }

    @Test
    void spaceFlipsOnlyTheSegmentBeingDrawnAndCollinearSegmentsMerge() {
        click(0, 0);
        click(4, 2);
        move(8, 6);
        assertThat(tool.flipElbow(context)).isTrue();
        doubleClick(8, 6);

        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(new GridPoint(0, 0), new GridPoint(4, 0)),
                tuple(new GridPoint(4, 0), new GridPoint(4, 6)),
                tuple(new GridPoint(4, 6), new GridPoint(8, 6)));
    }

    @Test
    void spaceDoesNothingWithoutAPath() {
        assertThat(tool.flipElbow(context)).isFalse();
    }

    @Test
    void collinearClicksMakeOneWire() {
        click(0, 0);
        click(3, 0);
        doubleClick(7, 0);

        assertThat(wires()).extracting(Wire::start, Wire::end)
                .containsExactly(tuple(new GridPoint(0, 0), new GridPoint(7, 0)));
    }

    @Test
    void aDragAddsASegmentAndKeepsDrawingFromTheRelease() {
        context.drag(tool, 0, 0, 5, 0);
        assertThat(tool.holdsGesture()).isTrue();
        assertThat(wires()).isEmpty();
        doubleClick(5, 4);

        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(new GridPoint(0, 0), new GridPoint(5, 0)),
                tuple(new GridPoint(5, 0), new GridPoint(5, 4)));
    }

    @Test
    void aClickOnAWireEndsThereWithAJunction() {
        addWire(context.openDocument(), 10, -5, 10, 5);
        click(0, 0);
        click(10, 0);

        assertThat(tool.holdsGesture()).isFalse();
        assertThat(wires()).extracting(Wire::start, Wire::end)
                .contains(tuple(new GridPoint(0, 0), new GridPoint(10, 0)));
    }

    @Test
    void crossingAWireInteriorDoesNotEndThePath() {
        addWire(context.openDocument(), 5, -5, 5, 5);
        click(0, 0);
        click(10, 0);

        assertThat(tool.holdsGesture()).isTrue();
        assertThat(wires()).hasSize(1);
    }

    @Test
    void aClickOnThePathsOwnCornerDoesNotEndIt() {
        click(0, 0);
        click(4, 0);
        click(4, 4);
        click(4, 0);

        assertThat(tool.holdsGesture()).isTrue();
        assertThat(wires()).isEmpty();
    }

    @Test
    void theModeIsReadWhenAPathStarts() {
        click(0, 0);
        mode.set(WireMode.PIECES);
        click(4, 0);
        assertThat(tool.holdsGesture()).isTrue();
        doubleClick(4, 3);
        assertThat(wires()).hasSize(2);

        context.drag(tool, 10, 0, 14, 0);
        assertThat(tool.holdsGesture()).isFalse();
        assertThat(wires()).hasSize(3);
    }

    @Test
    void inPiecesModeSpaceFlipsTheElbowOfTheDrag() {
        mode.set(WireMode.PIECES);
        tool.mousePressed(context, TestToolContext.at(0, 0));
        tool.mouseDragged(context, TestToolContext.at(4, 1));
        tool.mouseDragged(context, TestToolContext.at(6, 4));
        assertThat(tool.flipElbow(context)).isTrue();
        tool.mouseReleased(context, TestToolContext.at(6, 4));

        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(new GridPoint(0, 0), new GridPoint(0, 4)),
                tuple(new GridPoint(0, 4), new GridPoint(6, 4)));
    }

    @Test
    void thePreviewShowsTheFixedPathAndTheSegmentBeingDrawn() {
        click(0, 0);
        click(4, 0);
        move(4, 3);
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);

        assertThat(context.ghosts).hasSize(2).allSatisfy(ghost -> assertThat(ghost).isInstanceOf(Wire.class));
    }

    @Test
    void aDoubleClickWhosePointerWobbledAcrossAGridLineEndsAtTheLastCornerWithoutAStub() {
        click(0, 0);
        click(4, 2);
        click(4, 2.45, 1);
        click(4.55, 2.45, 2); // the second press snaps to (5, 2), a grid point away

        assertThat(tool.holdsGesture()).isFalse();
        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(new GridPoint(0, 0), new GridPoint(4, 0)),
                tuple(new GridPoint(4, 0), new GridPoint(4, 2)));
    }

    @Test
    void aDoubleClickAtTheStartWhosePointerWobbledAddsNoStrayWire() {
        click(0, 0, 1);
        click(0.55, 0.45, 2);

        assertThat(tool.holdsGesture()).isFalse();
        assertThat(wires()).isEmpty();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void aDoubleClickEndsAtTheFirstClicksPointEvenWhenTheSecondWobbledAlongTheLastLeg() {
        click(0, 0);
        click(4, 0, 1);
        click(4.55, 0, 2);

        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactly(
                tuple(new GridPoint(0, 0), new GridPoint(4, 0)));
    }
}
