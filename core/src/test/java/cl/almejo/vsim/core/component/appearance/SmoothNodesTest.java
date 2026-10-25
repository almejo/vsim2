package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.DefinitionPin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Cubic segments and the two handles of a node: a node between two curves is smooth when its handles lie on one line
 * through it, pointing away from each other; dragging one turns the other the opposite way with the same length, Alt
 * («alone») moves one only and makes the node a corner. The box is 6 × 4 units, 48 × 32 eighths.
 */
class SmoothNodesTest {
    private static final GridSize BOX = new GridSize(6, 4);
    /** An arch whose middle node (16, 16) has its handles at (8, 16) and (24, 16): smooth. */
    private static final String SMOOTH_ARCH = "M 0 32 C 0 24 8 16 16 16 C 24 16 32 24 32 32";
    /** The same nodes with the middle handles up-left and up-right: a corner. */
    private static final String CORNER_ARCH = "M 0 32 C 0 24 8 8 16 16 C 24 8 32 24 32 32";
    private static final int ARCH_NODE = 1;

    @Test
    void aNodeWhoseHandlesAreOppositeAndOnOneLineIsSmoothWhenLoaded() {
        assertThat(draft(SMOOTH_ARCH).smooth(0, ARCH_NODE)).isTrue();
        assertThat(draft(CORNER_ARCH).smooth(0, ARCH_NODE)).isFalse();
    }

    @Test
    void aHandleAnEighthOffTheLineStillCountsAsSmooth() {
        SymbolDraft rounded = draft("M 0 32 C 0 24 8 16 16 16 C 24 17 32 24 32 32");

        assertThat(rounded.smooth(0, ARCH_NODE)).as("rounding to the eighth must not turn a node into a corner")
                .isTrue();
    }

    @Test
    void draggingAHandleOfASmoothNodeTurnsTheOtherOppositeWithTheSameLength() {
        SymbolDraft moved = draft(SMOOTH_ARCH).moveHandle(0, ARCH_NODE, HandleSide.OUTGOING, new SymbolPoint(22, 10),
                false, SnapMode.FREE);

        SymbolPath arch = firstPath(moved);
        assertThat(arch.handle(ARCH_NODE, HandleSide.OUTGOING)).contains(new SymbolPoint(22, 10));
        assertThat(arch.handle(ARCH_NODE, HandleSide.INCOMING)).contains(new SymbolPoint(10, 22));
        assertThat(arch.nodes().get(ARCH_NODE).point()).as("the node stays put").isEqualTo(new SymbolPoint(16, 16));
        assertThat(moved.smooth(0, ARCH_NODE)).isTrue();
    }

    @Test
    void theMirroredHandleIsShortenedWhereTheBoxEndsWhileTheDraggedOneFollowsThePointer() {
        SymbolDraft moved = draft(SMOOTH_ARCH).moveHandle(0, ARCH_NODE, HandleSide.OUTGOING, new SymbolPoint(40, 16),
                false, SnapMode.FREE);

        SymbolPath arch = firstPath(moved);
        assertThat(arch.handle(ARCH_NODE, HandleSide.OUTGOING)).contains(new SymbolPoint(40, 16));
        assertThat(arch.handle(ARCH_NODE, HandleSide.INCOMING)).contains(new SymbolPoint(0, 16));
    }

    @Test
    void draggingAHandleAloneLeavesTheOtherAndMakesTheNodeACorner() {
        SymbolDraft moved = draft(SMOOTH_ARCH).moveHandle(0, ARCH_NODE, HandleSide.OUTGOING, new SymbolPoint(22, 10),
                true, SnapMode.FREE);

        SymbolPath arch = firstPath(moved);
        assertThat(arch.handle(ARCH_NODE, HandleSide.OUTGOING)).contains(new SymbolPoint(22, 10));
        assertThat(arch.handle(ARCH_NODE, HandleSide.INCOMING)).contains(new SymbolPoint(8, 16));
        assertThat(moved.smooth(0, ARCH_NODE)).isFalse();
    }

    @Test
    void aCornerNodeMovesOneHandleAtATime() {
        SymbolDraft moved = draft(CORNER_ARCH).moveHandle(0, ARCH_NODE, HandleSide.INCOMING, new SymbolPoint(4, 12),
                false, SnapMode.FREE);

        SymbolPath arch = firstPath(moved);
        assertThat(arch.handle(ARCH_NODE, HandleSide.INCOMING)).contains(new SymbolPoint(4, 12));
        assertThat(arch.handle(ARCH_NODE, HandleSide.OUTGOING)).contains(new SymbolPoint(24, 8));
    }

    @Test
    void togglingASmoothNodeMakesItACornerWithoutMovingAnythingAndIsOneUndoStep() {
        SymbolDraft smooth = draft(SMOOTH_ARCH);
        DraftHistory history = new DraftHistory(smooth);

        history.update(smooth.toggleSmooth(0, ARCH_NODE));

        assertThat(history.current().smooth(0, ARCH_NODE)).isFalse();
        assertThat(history.current().elements()).isEqualTo(smooth.elements());
        assertThat(history.canUndo()).isTrue();
        history.undo();
        assertThat(history.current().smooth(0, ARCH_NODE)).isTrue();
    }

    @Test
    void aCornerMadeSmoothAlignsItsHandlesOnTheBisectorKeepingTheirLengths() {
        SymbolDraft smooth = draft(CORNER_ARCH).toggleSmooth(0, ARCH_NODE);

        SymbolPath arch = firstPath(smooth);
        SymbolPoint incoming = arch.handle(ARCH_NODE, HandleSide.INCOMING).orElseThrow();
        SymbolPoint outgoing = arch.handle(ARCH_NODE, HandleSide.OUTGOING).orElseThrow();
        assertThat(incoming).isEqualTo(new SymbolPoint(5, 16));
        assertThat(outgoing).isEqualTo(new SymbolPoint(27, 16));
        assertThat(smooth.smooth(0, ARCH_NODE)).isTrue();
    }

    @Test
    void aCornerStaysACornerWhenItsHandleIsDraggedBackOntoTheLine() {
        SymbolDraft corner = draft(SMOOTH_ARCH).toggleSmooth(0, ARCH_NODE)
                .moveHandle(0, ARCH_NODE, HandleSide.OUTGOING, new SymbolPoint(28, 16), false, SnapMode.FREE);

        assertThat(corner.smooth(0, ARCH_NODE)).isFalse();
        assertThat(firstPath(corner).handle(ARCH_NODE, HandleSide.INCOMING)).contains(new SymbolPoint(8, 16));
    }

    @Test
    void aQuadraticReadFromAFileBecomesTheSameCurveAsACubicWhenItsHandleIsEdited() {
        SymbolDraft quadratic = draft("M 0 0 Q 12 24 24 0");
        SymbolPath before = firstPath(quadratic);
        assertThat(before.handle(0, HandleSide.OUTGOING)).contains(new SymbolPoint(8, 16));

        SymbolDraft edited = quadratic.moveHandle(0, 0, HandleSide.OUTGOING, new SymbolPoint(8, 16), false,
                SnapMode.FREE);

        SymbolPath after = firstPath(edited);
        assertThat(after.nodes().getFirst().curve()).contains(new SegmentCurve.Cubic(new SymbolPoint(8, 16),
                new SymbolPoint(16, 16)));
        assertThat(SymbolPathCodec.encode(after)).isEqualTo("M 0 0 C 8 16 16 16 24 0");
        for (int step = 0; step <= 8; step++) {
            double parameter = step / 8.0;
            assertThat(after.pointAt(0, parameter).x()).isCloseTo(before.pointAt(0, parameter).x(), within(1e-9));
            assertThat(after.pointAt(0, parameter).y()).isCloseTo(before.pointAt(0, parameter).y(), within(1e-9));
        }
    }

    @Test
    void aQuadraticLeftAloneIsWrittenBackAsAQuadratic() {
        SymbolDraft moved = draft("M 0 0 Q 12 24 24 0 L 40 8").moveHandle(0, 1, HandleSide.INCOMING,
                new SymbolPoint(16, 16), false, SnapMode.FREE);

        assertThat(SymbolPathCodec.encode(firstPath(moved))).isEqualTo("M 0 0 C 8 16 16 16 24 0 L 40 8");
        assertThat(SymbolPathCodec.encode(firstPath(draft("M 0 0 Q 12 24 24 0 L 40 8"))))
                .isEqualTo("M 0 0 Q 12 24 24 0 L 40 8");
    }

    @Test
    void curvingAStraightSegmentGivesACubicWithHandlesAtAThirdAndTwoThirds() {
        SymbolDraft line = draft("M 0 0 L 24 0");

        SymbolDraft flat = line.bendSegment(0, 0, 0.5, new SymbolPoint(12, 0), SnapMode.FREE);
        SymbolDraft bent = line.bendSegment(0, 0, 0.5, new SymbolPoint(12, 6), SnapMode.FREE);

        assertThat(firstPath(flat).nodes().getFirst().curve()).contains(new SegmentCurve.Cubic(new SymbolPoint(8, 0),
                new SymbolPoint(16, 0)));
        assertThat(firstPath(bent).nodes().getFirst().curve()).contains(new SegmentCurve.Cubic(new SymbolPoint(8, 8),
                new SymbolPoint(16, 8)));
        assertThat(firstPath(bent).pointAt(0, 0.5)).isEqualTo(new CurvePoint(12, 6));
    }

    @Test
    void draggingTheCurveBodyBendsItThroughTheGrabbedPoint() {
        SymbolDraft line = draft("M 0 16 C 8 16 16 16 24 16");
        double grabbed = line.nearestParameter(0, 0, new SymbolPoint(6, 16));

        SymbolPath bent = firstPath(line.bendSegment(0, 0, grabbed, new SymbolPoint(6, 20), SnapMode.FREE));

        CurvePoint point = bent.pointAt(0, grabbed);
        assertThat(point.x()).isCloseTo(6, within(1.0));
        assertThat(point.y()).isCloseTo(20, within(1.0));
    }

    @Test
    void straighteningACurvedSegmentDropsBothHandles() {
        SymbolPath straight = firstPath(draft(SMOOTH_ARCH).straighten(0, 0));

        assertThat(straight.nodes().getFirst().curve()).isEmpty();
        assertThat(straight.handle(ARCH_NODE, HandleSide.INCOMING)).isEmpty();
        assertThat(straight.handle(ARCH_NODE, HandleSide.OUTGOING)).contains(new SymbolPoint(24, 16));
    }

    @Test
    void movingANodeCarriesItsOwnHandles() {
        SymbolPath moved = firstPath(draft(SMOOTH_ARCH).moveNode(0, ARCH_NODE, new SymbolPoint(16, 20),
                SnapMode.FREE));

        assertThat(moved.handle(ARCH_NODE, HandleSide.INCOMING)).contains(new SymbolPoint(8, 20));
        assertThat(moved.handle(ARCH_NODE, HandleSide.OUTGOING)).contains(new SymbolPoint(24, 20));
        assertThat(moved.handle(0, HandleSide.OUTGOING)).as("the neighbour's handle stays")
                .contains(new SymbolPoint(0, 24));
    }

    @Test
    void insertingANodeInACubicKeepsItsOutlineAndTheNewNodeIsSmooth() {
        SymbolDraft arch = draft(SMOOTH_ARCH);
        SymbolPath before = firstPath(arch);

        SymbolDraft split = arch.insertNode(0, 0, new SymbolPoint(5, 22));

        SymbolPath after = firstPath(split);
        assertThat(after.nodes()).hasSize(4);
        assertThat(split.smooth(0, 1)).isTrue();
        CurvePoint middle = after.pointAt(0, 1);
        CurvePoint onCurve = before.pointAt(0, before.nearestParameter(0, middle.x(), middle.y()));
        assertThat(Math.hypot(onCurve.x() - middle.x(), onCurve.y() - middle.y())).isLessThan(1.0);
    }

    @Test
    void aCornerChoiceFollowsItsNodeWhenAnEarlierNodeIsInserted() {
        SymbolDraft corner = draft(SMOOTH_ARCH).toggleSmooth(0, ARCH_NODE);

        SymbolDraft inserted = corner.insertNode(0, 0, new SymbolPoint(5, 22));

        assertThat(inserted.smooth(0, ARCH_NODE + 1)).isFalse();
    }

    private static SymbolDraft draft(String data) {
        ElementId input = new ElementId("input");
        List<DefinitionPin> pins = List.of(new DefinitionPin(PinSpec.input("A", 0, 2), input));
        SymbolPath path = SymbolPathCodec.decode(data, false, BOX);
        return SymbolDraft.of(new ComponentAppearance(BOX, Optional.of(new SymbolDrawing(BOX, List.of(path), true)),
                List.of(new PinPlacement(input, new GridPoint(0, 2)))), pins, false);
    }

    private static SymbolPath firstPath(SymbolDraft draft) {
        return (SymbolPath) draft.elements().getFirst();
    }
}
