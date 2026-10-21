package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Axis;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.SnapMatch;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.snap.DefaultSnapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ajustar in the select tool (alignment-guides spec §3): NOT gates are 4×4, so a gate at (x, y) has the lines x,
 * x + 2, x + 4 and y, y + 2, y + 4. At 100 % the magnet reaches one unit.
 */
class SelectToolSnapTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();
    private Component anchor;

    @BeforeEach
    void create() {
        context.snapper = new DefaultSnapper(context.preferences, Dimension::new);
        anchor = gate(0, 0);
    }

    @AfterEach
    void close() {
        context.close();
    }

    private Component gate(int x, int y) {
        return place(context.openDocument(), StandardComponentTypes.NOT, x, y, Map.of());
    }

    private GridPoint position(Component component) {
        return context.openDocument().document().component(component.id()).orElseThrow().position();
    }

    /** Presses the middle of {@code component} and drags it by the raw delta, Ctrl held after the press if asked. */
    private void pressAndDrag(Component component, int deltaX, int deltaY, boolean controlDown) {
        Bounds bounds = ElementBounds.of(component, context.openDocument().registry());
        double centreX = bounds.x() + bounds.width() / 2.0;
        double centreY = bounds.y() + bounds.height() / 2.0;
        tool.mousePressed(context, TestToolContext.at(centreX, centreY)); // Ctrl at the press would toggle
        tool.mouseDragged(context, TestToolContext.withModifiers(centreX + deltaX, centreY + deltaY, false,
                controlDown));
    }

    private void release(Component component, int deltaX, int deltaY, boolean controlDown) {
        Bounds bounds = ElementBounds.of(component, context.openDocument().registry());
        tool.mouseReleased(context, TestToolContext.withModifiers(bounds.x() + bounds.width() / 2.0 + deltaX,
                bounds.y() + bounds.height() / 2.0 + deltaY, false, controlDown));
    }

    private void move(Component component, int deltaX, int deltaY) {
        pressAndDrag(component, deltaX, deltaY, false);
        release(component, deltaX, deltaY, false);
    }

    @Test
    void aDragEndingOneUnitOffANeighboursTopEdgeLandsAlignedAndShowsTheLine() {
        Component moved = gate(20, 10);
        pressAndDrag(moved, 0, -9, false);

        assertThat(tool.alignmentMatches()).extracting(SnapMatch::axis, SnapMatch::position)
                .contains(org.assertj.core.groups.Tuple.tuple(Axis.Y, 0.0));
        tool.paintOverlay(TestToolContext.scratchGraphics(), context);
        release(moved, 0, -9, false);

        assertThat(position(moved)).isEqualTo(new GridPoint(20, 0));
        assertThat(tool.alignmentMatches()).isEmpty();
    }

    @Test
    void withAjustarOffTheMoveLandsWhereThePointerSays() {
        context.preferences.setSnapToAlignments(false);
        Component moved = gate(20, 10);
        move(moved, 0, -9);
        assertThat(position(moved)).isEqualTo(new GridPoint(20, 1));
    }

    @Test
    void withPiezasOffPartsDoNotPull() {
        context.preferences.setSnapToParts(false);
        Component moved = gate(20, 10);
        move(moved, 0, -9);
        assertThat(position(moved)).isEqualTo(new GridPoint(20, 1));
    }

    @Test
    void aCtrlCopyAlignsWithItsOriginal() {
        context.selection.select(anchor.id());
        pressAndDrag(anchor, 1, 20, true);
        release(anchor, 1, 20, true);

        assertThat(position(anchor)).isEqualTo(GridPoint.ORIGIN);
        assertThat(context.openDocument().document().components()).extracting(Component::position)
                .containsExactlyInAnyOrder(GridPoint.ORIGIN, new GridPoint(0, 20));
    }

    @Test
    void switchingCtrlMidDragSwitchesTheExclusions() {
        context.selection.select(anchor.id());
        pressAndDrag(anchor, 1, 20, true);
        assertThat(tool.alignmentMatches()).extracting(SnapMatch::axis).contains(Axis.X); // the copy's column
        // Ctrl let go: the gate itself is no target, so one unit right stays one unit right
        tool.modifiersChanged(context, false, false, false);
        release(anchor, 1, 20, false);
        assertThat(position(anchor)).isEqualTo(new GridPoint(1, 20));
    }

    @Test
    void aLockedFramesWallStillWinsAndOnlyTheLinesThatHoldAreDrawn() {
        Frame frame = new Frame(context.openDocument().document().newFrameId(), new GridPoint(30, 0), 20, 20,
                "Bloque", FrameTint.BLUE, true);
        context.openDocument().execute(new CreateFrameCommand(frame));
        Component inside = gate(36, 5); // its bottom at 9; the wall at 20
        Component outside = gate(60, 17); // its top at 17, its bottom at 21: one past the wall
        // raw 11 puts the bottom on the wall (20); the magnet would take it to 21, the wall keeps it at 20
        move(inside, 0, 11);
        assertThat(position(inside)).isEqualTo(new GridPoint(36, 16));
        assertThat(position(outside)).isEqualTo(new GridPoint(60, 17));

        pressAndDrag(context.openDocument().document().component(inside.id()).orElseThrow(), 0, 2, false);
        assertThat(tool.alignmentMatches()).extracting(SnapMatch::position).doesNotContain(21.0);
    }

    @Test
    void togglingAjustarMidDragReEvaluatesAndAReleaseRightAfterCommitsIt() {
        Component moved = gate(20, 10);
        pressAndDrag(moved, 0, -9, false);
        context.preferences.setSnapToAlignments(false);
        assertThat(tool.refreshSnap(context)).isTrue();
        assertThat(tool.alignmentMatches()).isEmpty();
        release(moved, 0, -9, false);
        assertThat(position(moved)).isEqualTo(new GridPoint(20, 1));
    }

    @Test
    void zoomingOutMidDragWidensTheMagnetAndAReleaseRightAfterCommitsIt() {
        Component moved = gate(20, 10);
        // the top at 7: three units below the anchor's bottom (4), beyond the 100 % reach
        pressAndDrag(moved, 0, -3, false);
        assertThat(tool.alignmentMatches()).isEmpty();
        context.viewport.zoomAt(0, 0, 0.25);
        assertThat(tool.refreshSnap(context)).isTrue();
        release(moved, 0, -3, false);
        assertThat(position(moved)).isEqualTo(new GridPoint(20, 4));
    }

    @Test
    void aGuideWinsATieWithAPartAndAGuideChangeIsPickedUpByTheRefresh() {
        Component moved = gate(20, 10);
        pressAndDrag(moved, 2, -9, false); // left edge at 22, top at 1
        context.openDocument().document().setGuides(List.of(new Guide(Guide.Orientation.VERTICAL, 23),
                new Guide(Guide.Orientation.HORIZONTAL, 2)));
        assertThat(tool.refreshSnap(context)).isTrue();
        release(moved, 2, -9, false);
        // x: 22 → 23 (the guide); y: the anchor's top (0) and the guide (2) are both one unit away: the guide wins
        assertThat(position(moved)).isEqualTo(new GridPoint(23, 2));
    }

    @Test
    void hiddenGuidesOrGuidasOffDoNotPull() {
        context.openDocument().document().setGuides(List.of(new Guide(Guide.Orientation.VERTICAL, 23)));
        context.preferences.setShowGuides(false);
        Component first = gate(20, 30);
        move(first, 2, 0);
        assertThat(position(first)).isEqualTo(new GridPoint(22, 30));

        context.preferences.setShowGuides(true);
        context.preferences.setSnapToGuides(false);
        Component second = gate(20, 50);
        move(second, 2, 0);
        assertThat(position(second)).isEqualTo(new GridPoint(22, 50));
    }

    @Test
    void gridLinesPullOnlyWhenChosenAndNothingElseIsNear() {
        context.preferences.setSnapToParts(false);
        context.preferences.setSnapToGridLines(true);
        Component moved = gate(21, 31);
        move(moved, 2, 2); // to (23, 33): the left and top edges go to the even lines below
        assertThat(position(moved).x() % 2).isZero();
        assertThat(position(moved).y() % 2).isZero();
        assertThat(tool.alignmentMatches()).isEmpty();
    }

    @Test
    void theGridLinesPullTheTopLeftCornerToMultiplesOfTheChosenSpacing() {
        context.preferences.setSnapToParts(false);
        context.preferences.setSnapToGridLines(true);
        context.preferences.setGridLineSpacing(10);
        Component moved = gate(21, 31);
        move(moved, 8, 18); // raw (29, 49): the lines 30 and 50 are one unit away
        assertThat(position(moved)).isEqualTo(new GridPoint(30, 50));
        context.preferences.setGridLineSpacing(4);
        Component landed = context.openDocument().document().component(moved.id()).orElseThrow();
        move(landed, -5, -1); // raw (25, 49): the lines 24 and 48 are one unit away
        assertThat(position(moved)).isEqualTo(new GridPoint(24, 48));
    }

    @Test
    void changingTheSpacingMidDragRefreshesThePreviewAndTheReleaseCommitsIt() {
        context.preferences.setSnapToParts(false);
        context.preferences.setSnapToGridLines(true);
        context.preferences.setGridLineSpacing(5);
        Component moved = gate(21, 31);
        pressAndDrag(moved, 4, 18, false); // raw (25, 49): (25, 50) on the fives
        context.preferences.setGridLineSpacing(4);
        tool.refreshSnap(context);
        tool.mouseReleased(context, TestToolContext.at(23 + 4, 33 + 18));
        assertThat(position(moved)).isEqualTo(new GridPoint(24, 48));
    }

    @Test
    void aWireOnlySelectionSnapsItsEndsToAGuide() {
        Wire wire = new Wire(context.openDocument().document().newWireId(), new GridPoint(0, 40),
                new GridPoint(10, 40));
        context.openDocument().execute(new AddWireCommand(wire));
        context.openDocument().document().setGuides(List.of(new Guide(Guide.Orientation.VERTICAL, 13)));
        context.selection.select(wire.id());
        tool.mousePressed(context, TestToolContext.at(5, 40));
        tool.mouseDragged(context, TestToolContext.at(7, 45));
        tool.mouseReleased(context, TestToolContext.at(7, 45));
        assertThat(context.openDocument().document().wires()).singleElement()
                .satisfies(moved -> {
                    assertThat(moved.start()).isEqualTo(new GridPoint(3, 45));
                    assertThat(moved.end()).isEqualTo(new GridPoint(13, 45));
                });
    }

    @Test
    void aWireOnlySelectionIgnoresParts() {
        Wire wire = new Wire(context.openDocument().document().newWireId(), new GridPoint(20, 40),
                new GridPoint(30, 40));
        context.openDocument().execute(new AddWireCommand(wire));
        context.selection.select(wire.id());
        tool.mousePressed(context, TestToolContext.at(25, 40));
        tool.mouseDragged(context, TestToolContext.at(25, 35)); // y 35, nothing but the anchor's lines (0..4) far
        tool.mouseDragged(context, TestToolContext.at(25, 5)); // y 5: one off the anchor's bottom, a part
        tool.mouseReleased(context, TestToolContext.at(25, 5));
        assertThat(context.openDocument().document().wires()).singleElement()
                .satisfies(moved -> assertThat(moved.start()).isEqualTo(new GridPoint(20, 5)));
    }

    @Test
    void aTextOnlySelectionNeverSnaps() {
        context.openDocument().document().setGuides(List.of(new Guide(Guide.Orientation.VERTICAL, 23)));
        TextLabel text = new TextLabel(context.openDocument().document().newLabelId(), new GridPoint(20, 30),
                "nota");
        context.openDocument().execute(new AddElementsCommand(List.of(text)));
        context.selection.select(text.id());
        tool.mousePressed(context, TestToolContext.at(20, 30));
        tool.mouseDragged(context, TestToolContext.at(22, 30));
        tool.mouseReleased(context, TestToolContext.at(22, 30));
        assertThat(context.openDocument().document().element(text.id()).orElseThrow())
                .isInstanceOfSatisfying(TextLabel.class,
                        moved -> assertThat(moved.position()).isEqualTo(new GridPoint(22, 30)));
    }

    @Test
    void aSegmentDragSnapsAcrossTheWireAndKeepsItsAxisLock() {
        Wire wire = new Wire(context.openDocument().document().newWireId(), new GridPoint(20, 40),
                new GridPoint(30, 40));
        context.openDocument().execute(new AddWireCommand(wire));
        context.openDocument().document().setGuides(List.of(new Guide(Guide.Orientation.HORIZONTAL, 46),
                new Guide(Guide.Orientation.VERTICAL, 21)));
        context.selection.select(wire.id());
        context.dragHolding(tool, 25, 40, 26, 45, false, true);
        assertThat(context.openDocument().document().wire(wire.id()).orElseThrow())
                .satisfies(moved -> {
                    assertThat(moved.start()).isEqualTo(new GridPoint(20, 46));
                    assertThat(moved.end()).isEqualTo(new GridPoint(30, 46));
                });
    }

    @Test
    void aClickThatJittersNeverBecomesAMove() {
        Component moved = gate(20, 1); // its top one unit off the anchor's
        Bounds bounds = ElementBounds.of(moved, context.openDocument().registry());
        double centreX = bounds.x() + 2;
        double centreY = bounds.y() + 2;
        tool.mousePressed(context, TestToolContext.at(centreX, centreY));
        tool.mouseDragged(context, TestToolContext.at(centreX + 0.2, centreY));
        tool.mouseReleased(context, TestToolContext.at(centreX + 0.2, centreY));
        assertThat(position(moved)).isEqualTo(new GridPoint(20, 1));
    }

    @Test
    void escapeForgetsTheLines() {
        Component moved = gate(20, 10);
        pressAndDrag(moved, 0, -9, false);
        tool.keyPressed(context, KeyEvent.VK_ESCAPE, false);
        assertThat(tool.alignmentMatches()).isEmpty();
        assertThat(tool.refreshSnap(context)).isFalse();
        assertThat(position(moved)).isEqualTo(new GridPoint(20, 10));
    }

    @Test
    void aDragTheMagnetPullsBackToZeroKeepsTheSelectionAndMovesNothing() {
        Component first = gate(20, 0);
        Component second = gate(30, 0);
        context.selection.set(List.of(first.id(), second.id()));
        String before = context.openDocument().history().undoDescription().toString();
        tool.mousePressed(context, TestToolContext.at(22, 2));
        tool.mouseDragged(context, TestToolContext.at(22, 3)); // the tops go back to the anchor's
        tool.mouseReleased(context, TestToolContext.at(22, 3));
        assertThat(context.selection.ids()).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(context.openDocument().history().undoDescription().toString()).isEqualTo(before);
        assertThat(position(first)).isEqualTo(new GridPoint(20, 0));
    }

    @Test
    void aSwitchDraggedBackToItsPlaceByTheMagnetIsNotToggled() {
        Component lever = place(context.openDocument(), StandardComponentTypes.SWITCH, 20, 0,
                Map.of("value", "1"));
        gate(20, 10); // its left, centre and right lines are the switch's
        waitUntil(() -> context.openDocument().latestSnapshot().componentState(lever.id().value())
                .equals(new VisibleState.SwitchState(Value.ONE)));
        Bounds bounds = ElementBounds.of(lever, context.openDocument().registry());
        double centreX = bounds.x() + bounds.width() / 2.0;
        double centreY = bounds.y() + bounds.height() / 2.0;
        tool.mousePressed(context, TestToolContext.at(centreX, centreY));
        tool.mouseDragged(context, TestToolContext.at(centreX + 1, centreY));
        tool.mouseReleased(context, TestToolContext.at(centreX + 1, centreY));
        assertThat(position(lever)).isEqualTo(new GridPoint(20, 0));
        assertThat(awaitSimulation(context.openDocument()).componentState(lever.id().value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ONE));
    }
}
