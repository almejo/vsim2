package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Selección draws wires with the continuous rules (the 2026-11-09 change: no Cable tool): a click on a pin starts a
 * path, each click fixes a corner, a click on a pin or a wire ends; a drag from a pin or an unselected wire adds one
 * segment and keeps drawing. The paths here start at the output pin of a buffer placed so that the pin is at (0, 0).
 */
class SelectToolContinuousWireTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = (SelectTool) context.defaultTool;
    private Component startPart;

    @BeforeEach
    void placeTheStartPart() {
        Messages.initialize(Messages.SPANISH);
        Component probe = buffer(0, 0);
        GridPoint output = pin(probe, 1);
        context.openDocument().history().undo();
        startPart = buffer(-output.x(), -output.y());
        assertThat(pin(startPart, 1)).isEqualTo(new GridPoint(0, 0));
        context.openDocument().history().clear();
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

    private void rightClick(double gridX, double gridY) {
        tool.mousePressed(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.SECONDARY, 1, false));
        tool.mouseReleased(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.SECONDARY, 1, false));
    }

    private GridPoint pin(Component component, int pinIndex) {
        return context.openDocument().registry().typeOf(component).pinLocation(component, pinIndex);
    }

    private Component buffer(int x, int y) {
        return place(context.openDocument(), StandardComponentTypes.BUFFER, x, y,
                Map.of(StandardComponentTypes.NEGATE, true));
    }

    private boolean drawing() {
        return tool.drawsBetweenClicks();
    }

    @Test
    void aClickOnAPinThenAClickOnAnotherPinIsOneWireAndOneUndoStep() {
        Component second = buffer(20, 6);
        GridPoint to = pin(second, 0);
        context.openDocument().history().clear(); // only the wire is in the history

        click(0, 0);
        assertThat(drawing()).isTrue();
        click(to.x(), to.y());

        assertThat(drawing()).isFalse();
        assertThat(wires()).isNotEmpty();
        assertThat(context.tool).isSameAs(tool);
        assertThat(context.openDocument().history().undoDescription()).contains("command.wire");
        context.openDocument().history().undo();
        assertThat(wires()).isEmpty();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void cornersInEmptySpaceEndWithADoubleClickAsOneUndoStep() {
        click(0, 0);
        click(4, 2);
        doubleClick(9, 2);

        assertThat(drawing()).isFalse();
        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(new GridPoint(0, 0), new GridPoint(4, 0)),
                tuple(new GridPoint(4, 0), new GridPoint(4, 2)),
                tuple(new GridPoint(4, 2), new GridPoint(9, 2)));
        context.openDocument().history().undo();
        assertThat(wires()).isEmpty();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void aClickInEmptySpaceStartsNoWire() {
        click(6, 6);

        assertThat(drawing()).isFalse();
        click(10, 6);
        assertThat(wires()).isEmpty();
    }

    @Test
    void aDragFromAPinToEmptySpaceAddsASegmentAndKeepsDrawing() {
        context.drag(tool, 0, 0, 5, 0);

        assertThat(drawing()).isTrue();
        assertThat(wires()).isEmpty();
        doubleClick(5, 4);
        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(new GridPoint(0, 0), new GridPoint(5, 0)),
                tuple(new GridPoint(5, 0), new GridPoint(5, 4)));
    }

    @Test
    void aDragFromAPinReleasedOnAPinEndsThereInOneGesture() {
        Component second = buffer(20, 6);
        GridPoint to = pin(second, 0);

        context.drag(tool, 0, 0, to.x(), to.y());

        assertThat(drawing()).isFalse();
        assertThat(wires()).isNotEmpty();
        context.openDocument().history().undo();
        assertThat(wires()).isEmpty();
    }

    @Test
    void aDragFromAnUnselectedWireStartsAPath() {
        addWire(context.openDocument(), 10, -5, 10, 5);

        context.drag(tool, 10, 0, 14, 0);

        assertThat(drawing()).isTrue();
        doubleClick(14, 3);
        assertThat(wires()).extracting(Wire::start, Wire::end).contains(
                tuple(new GridPoint(10, 0), new GridPoint(14, 0)),
                tuple(new GridPoint(14, 0), new GridPoint(14, 3)));
    }

    @Test
    void aClickOnAWireSelectsItAndStartsNothing() {
        Wire existing = addWire(context.openDocument(), 10, -5, 10, 5);

        click(10, 0);

        assertThat(drawing()).isFalse();
        assertThat(context.selection.ids()).containsExactly(existing.id());
        assertThat(wires()).hasSize(1);
    }

    @Test
    void aClickOnAPartsBodySelectsIt() {
        Component part = buffer(20, 6);
        GridPoint input = pin(part, 0);
        GridPoint output = pin(part, 1);

        click((input.x() + output.x()) / 2.0, (input.y() + output.y()) / 2.0);

        assertThat(drawing()).isFalse();
        assertThat(context.selection.ids()).containsExactly(part.id());
    }

    @Test
    void aClickOnAPinOfASelectedPartInASelectionOfSeveralStartsAPath() {
        Component other = buffer(20, 6);
        context.selection.set(Set.of(startPart.id(), other.id()));

        click(0, 0);

        assertThat(drawing()).isTrue();
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(startPart.id(), other.id());
    }

    @Test
    void aDragFromAPinOfASelectedPartInASelectionOfSeveralMovesTheSelection() {
        Component other = buffer(20, 6);
        context.selection.set(Set.of(startPart.id(), other.id()));
        GridPoint before = other.position();

        context.drag(tool, 0, 0, 3, 2);

        assertThat(drawing()).isFalse();
        assertThat(wires()).isEmpty();
        assertThat(context.openDocument().document().component(other.id()).orElseThrow().position())
                .isEqualTo(before.translate(3, 2));
    }

    @Test
    void aDragInEmptySpaceIsStillTheRubberBand() {
        context.drag(tool, 30, 30, 40, 40);

        assertThat(drawing()).isFalse();
        assertThat(wires()).isEmpty();
        tool.mousePressed(context, TestToolContext.at(30, 30));
        tool.mouseDragged(context, TestToolContext.at(40, 40));
        assertThat(tool.rubberBand()).isNotNull();
        tool.mouseReleased(context, TestToolContext.at(40, 40));
    }

    @Test
    void aRightClickWithNoPathOpensTheContextMenu() {
        rightClick(20, 20);

        assertThat(context.contextMenuTarget).isNotNull();
        assertThat(context.tool).isSameAs(tool);
    }

    @Test
    void aRightClickWithAPathEndsItWhereThePointerIsWithoutTheContextMenu() {
        click(0, 0);
        rightClick(4, 0);

        assertThat(drawing()).isFalse();
        assertThat(context.contextMenuTarget).isNull();
        assertThat(wires()).extracting(Wire::start, Wire::end)
                .containsExactly(tuple(new GridPoint(0, 0), new GridPoint(4, 0)));
    }

    @Test
    void escapeCancelsThePathAndKeepsTheSelectionThenClearsItAsBefore() {
        Component other = buffer(20, 6);
        context.selection.select(other.id());
        click(0, 0);
        click(4, 0);

        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(drawing()).isFalse();
        assertThat(wires()).isEmpty();
        assertThat(context.selection.ids()).containsExactly(other.id());
        assertThat(tool.keyPressed(context, KeyEvent.VK_ESCAPE, false)).isTrue();
        assertThat(context.selection.ids()).isEmpty();
    }

    @Test
    void backspaceDropsTheLastCornerThenCancelsThenIsLeftToTheDeleteBinding() {
        click(0, 0);
        click(4, 0);
        move(4, 4);

        assertThat(tool.keyPressed(context, KeyEvent.VK_BACK_SPACE, false)).isTrue();
        assertThat(drawing()).isTrue();
        assertThat(tool.keyPressed(context, KeyEvent.VK_BACK_SPACE, false)).isTrue();
        assertThat(drawing()).isFalse();
        assertThat(tool.keyPressed(context, KeyEvent.VK_BACK_SPACE, false)).isFalse();
        assertThat(wires()).isEmpty();
    }

    @Test
    void dropLastStepDropsACornerAndIsUnusedWithoutAPath() {
        assertThat(tool.dropLastStep(context)).isFalse();
        click(0, 0);
        click(4, 0);
        assertThat(tool.dropLastStep(context)).isTrue();
        assertThat(tool.dropLastStep(context)).isTrue();
        assertThat(drawing()).isFalse();
        assertThat(tool.dropLastStep(context)).isFalse();
    }

    @Test
    void spaceFlipsOnlyTheSegmentBeingDrawn() {
        assertThat(tool.flipElbow(context)).isFalse();
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
    void aClickOnAWireEndsThePath() {
        addWire(context.openDocument(), 10, -5, 10, 5);
        click(0, 0);
        click(10, 0);

        assertThat(drawing()).isFalse();
        assertThat(wires()).extracting(Wire::start, Wire::end)
                .contains(tuple(new GridPoint(0, 0), new GridPoint(10, 0)));
    }

    @Test
    void crossingAWireInteriorDoesNotEndThePath() {
        addWire(context.openDocument(), 5, -5, 5, 5);
        click(0, 0);
        click(10, 0);

        assertThat(drawing()).isTrue();
        assertThat(wires()).hasSize(1);
    }

    @Test
    void aDoubleClickEndingOnAPinNeverConfiguresItsPart() {
        Component second = buffer(20, 6);
        GridPoint to = pin(second, 0);
        click(0, 0);

        doubleClick(to.x(), to.y());

        assertThat(drawing()).isFalse();
        assertThat(context.configured).isNull();
        assertThat(context.entered).isNull();
        assertThat(wires()).isNotEmpty();
    }

    @Test
    void theSecondPressOfADoubleClickThatEndedOnAWireNeitherStartsNorSelectsTheStretch() {
        addWire(context.openDocument(), 10, -5, 10, 5);
        click(0, 0);
        doubleClick(10, 0);

        assertThat(drawing()).isFalse();
        assertThat(wires()).hasSize(2);
        assertThat(context.selection.ids()).isEmpty();
    }

    @Test
    void aDoubleClickOnAPinWithNoPathAddsNothing() {
        doubleClick(0, 0);

        assertThat(drawing()).isFalse();
        assertThat(wires()).isEmpty();
        assertThat(context.openDocument().history().canUndo()).isFalse();
    }

    @Test
    void anInterruptionCancelsThePathAndSaysSo() {
        click(0, 0);
        click(4, 0);

        tool.deactivated(context);

        assertThat(drawing()).isFalse();
        assertThat(context.notices).contains("Cable cancelado");
        assertThat(wires()).isEmpty();
    }

    @Test
    void whileDrawingTheToolIsNotIdleAndTheGestureCrossesPanes() {
        click(0, 0);

        assertThat(tool.isIdle()).isFalse();
        assertThat(tool.crossesPanes()).isTrue();
        assertThat(tool.holdsGesture()).isTrue();
    }

    @Test
    void theModeIsReadWhenAPathStarts() {
        click(0, 0);
        context.preferences.setWireMode(WireMode.PIECES);
        click(4, 0);
        assertThat(drawing()).isTrue();
        doubleClick(4, 3);
        assertThat(wires()).hasSize(2);
    }

    @Test
    void inPiecesModeAClickOnAPinSelectsThePartAndADragDrawsOneL() {
        context.preferences.setWireMode(WireMode.PIECES);

        click(0, 0);
        assertThat(drawing()).isFalse();
        assertThat(context.selection.ids()).containsExactly(startPart.id());

        context.selection.clear();
        context.drag(tool, 0, 0, 5, 3);
        assertThat(drawing()).isFalse();
        assertThat(wires()).hasSize(2);
    }

    @Test
    void inPiecesModeSpaceFlipsTheElbowOfTheDrag() {
        context.preferences.setWireMode(WireMode.PIECES);
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
    void afterADragThatKeepsDrawingThePreviewShowsAtOnceWhereTheButtonWasReleased() {
        context.drag(tool, 0, 0, 5, 0);

        BufferedImage image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(40, 60);
        tool.paintOverlay(graphics, context);
        graphics.dispose();

        int releaseX = 40 + 5 * GridPoint.PIXELS_PER_UNIT;
        assertThat(paintedPixelsAround(image, releaseX, 60, 7)).isPositive();
    }

    @Test
    void thePreviewShowsTheFixedPathAsGhostsWhileTheSegmentBeingDrawnIsDashed() {
        click(0, 0);
        click(4, 0);
        move(4, 3);
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);

        assertThat(context.ghosts).extracting(ghost -> ((Wire) ghost).start(), ghost -> ((Wire) ghost).end())
                .containsExactly(tuple(new GridPoint(0, 0), new GridPoint(4, 0)));
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
    void collinearClicksMakeOneWire() {
        click(0, 0);
        click(3, 0);
        doubleClick(7, 0);

        assertThat(wires()).extracting(Wire::start, Wire::end)
                .containsExactly(tuple(new GridPoint(0, 0), new GridPoint(7, 0)));
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
    void aClickOnThePathsOwnCornerDoesNotEndIt() {
        click(0, 0);
        click(4, 0);
        click(4, 4);
        click(4, 0);

        assertThat(drawing()).isTrue();
        assertThat(wires()).isEmpty();
    }

    @Test
    void aDoubleClickWhosePointerWobbledAcrossAGridLineEndsAtTheLastCornerWithoutAStub() {
        click(0, 0);
        click(4, 2);
        click(4, 2.45, 1);
        click(4.55, 2.45, 2); // the second press snaps to (5, 2), a grid point away

        assertThat(drawing()).isFalse();
        assertThat(wires()).extracting(Wire::start, Wire::end).containsExactlyInAnyOrder(
                tuple(new GridPoint(0, 0), new GridPoint(4, 0)),
                tuple(new GridPoint(4, 0), new GridPoint(4, 2)));
    }

    @Test
    void deactivatingWithoutAPathSaysNothing() {
        tool.deactivated(context);

        assertThat(context.notices).isEmpty();
    }

    @Test
    void anInterruptionLeavesTheNoticeToTheCallerAndADragInPiecesAsItIs() {
        click(0, 0);
        assertThat(tool.interrupt(context)).contains("Cable cancelado");
        assertThat(context.notices).isEmpty();
        assertThat(tool.interrupt(context)).isEmpty();

        context.preferences.setWireMode(WireMode.PIECES);
        tool.mousePressed(context, TestToolContext.at(0, 0));
        tool.mouseDragged(context, TestToolContext.at(4, 2));
        assertThat(tool.interrupt(context)).isEmpty();
        assertThat(drawing()).isFalse();
    }

    @Test
    void losingTheFocusKeepsThePath() {
        click(0, 0);
        click(4, 2);

        tool.focusLost(context);

        assertThat(drawing()).isTrue();
        doubleClick(8, 6);
        assertThat(wires()).isNotEmpty();
    }

    private static int paintedPixelsAround(BufferedImage image, int centreX, int centreY, int reach) {
        int painted = 0;
        for (int x = centreX - reach; x <= centreX + reach; x++) {
            for (int y = centreY - reach; y <= centreY + reach; y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    painted++;
                }
            }
        }
        return painted;
    }
}
