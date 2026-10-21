package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.SnapKind;
import cl.almejo.vsim.core.geometry.SnapResult;
import cl.almejo.vsim.core.geometry.SnapTarget;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The canvas's snapper: preferences, reach by zoom, visible targets, exclusions and the per-gesture cache. */
class DefaultSnapperTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private Dimension canvasSize = new Dimension();
    private final DefaultSnapper snapper = new DefaultSnapper(context.preferences, () -> canvasSize);
    private Component anchor;

    @BeforeEach
    void create() {
        anchor = place(context.openDocument(), StandardComponentTypes.NOT, 0, 0, Map.of());
    }

    @AfterEach
    void close() {
        context.close();
    }

    /** A 4×4 box at (20, y) moved straight up by {@code rawDeltaY}. */
    private SnapResult moveBoxAt(int y, int rawDeltaY, Set<ElementId> excluded) {
        MovingSet moving = new MovingSet(excluded, Optional.of(new Bounds(20, y, 4, 4)),
                context.openDocument().registry());
        return snapper.snapBox(context, moving, 0, rawDeltaY);
    }

    @ParameterizedTest
    @CsvSource({"0.25, 6", "0.5, 3", "1.0, 1", "2.0, 1", "4.0, 1"})
    void theReachFollowsTheZoom(double zoom, int reachUnits) {
        context.viewport.zoomAt(0, 0, zoom);
        // the box's top lands reachUnits below the anchor's bottom (4): just within reach
        assertThat(moveBoxAt(10, 4 + reachUnits - 10, Set.of()).deltaY()).isEqualTo(4 - 10);
        // one more unit is out of reach (and odd, so no other pair of lines is closer)
        int beyond = 4 + reachUnits + 1 - 10;
        SnapResult far = moveBoxAt(10, beyond, Set.of());
        assertThat(far.winnerY()).isEmpty();
        assertThat(far.deltaY()).isEqualTo(beyond);
    }

    @Test
    void ajustarOffOrNothingChosenSnapsNothing() {
        context.preferences.setSnapToAlignments(false);
        assertThat(moveBoxAt(10, -9, Set.of()).deltaY()).isEqualTo(-9);
        context.preferences.setSnapToAlignments(true);
        context.preferences.setSnapToParts(false);
        context.preferences.setSnapToGuides(false);
        assertThat(moveBoxAt(10, -9, Set.of())).isEqualTo(SnapResult.unsnapped(0, -9));
    }

    @Test
    void anExcludedPartNeverPulls() {
        assertThat(moveBoxAt(10, -9, Set.of(anchor.id())).deltaY()).isEqualTo(-9);
        assertThat(moveBoxAt(10, -9, Set.of()).deltaY()).isEqualTo(-10);
    }

    @Test
    void aMovingSetWithoutABoxNeverSnaps() {
        MovingSet empty = new MovingSet(Set.of(), Optional.empty(), context.openDocument().registry());
        assertThat(snapper.snapBox(context, empty, 3, -9)).isEqualTo(SnapResult.unsnapped(3, -9));
    }

    @Test
    void onlyPartsOnScreenPull() {
        canvasSize = new Dimension(400, 300); // grid 0..50 × 0..37.5 at 100 %
        Component shown = place(context.openDocument(), StandardComponentTypes.NOT, 200, 0, Map.of());
        context.viewport.panBy(-1200, 0); // shows grid 150..200: the anchor is off screen, the other gate on it
        SnapResult result = moveBoxAt(10, -9, Set.of());
        assertThat(result.winnerY()).flatMap(SnapTarget::ownerId).contains(shown.id());
    }

    @Test
    void thePartLinesAreWorkedOutOncePerGestureAndAgainAfterAnEdit() {
        for (int step = 0; step < 50; step++) {
            moveBoxAt(10, -step, Set.of());
        }
        assertThat(snapper.partTargetBuilds()).isEqualTo(1);
        place(context.openDocument(), StandardComponentTypes.NOT, 40, 40, Map.of());
        moveBoxAt(10, -9, Set.of());
        assertThat(snapper.partTargetBuilds()).isEqualTo(2);
        moveBoxAt(10, -9, Set.of(anchor.id()));
        assertThat(snapper.partTargetBuilds()).isEqualTo(3);
    }

    @Test
    void guidesPullWhileShownAndChosenAndWinTies() {
        context.openDocument().document().setGuides(List.of(new Guide(Guide.Orientation.HORIZONTAL, 2)));
        SnapResult result = moveBoxAt(10, -9, Set.of()); // top at 1: the anchor's top 0 and the guide 2 tie
        assertThat(result.winnerY()).map(SnapTarget::kind).contains(SnapKind.GUIDE);
        assertThat(result.deltaY()).isEqualTo(-8);
        context.preferences.setShowGuides(false);
        assertThat(moveBoxAt(10, -9, Set.of()).winnerY()).map(SnapTarget::kind).contains(SnapKind.PART);
    }

    @Test
    void wiresSnapTheirEndsToGuidesAndGridLinesOnly() {
        Wire wire = new Wire(context.openDocument().document().newWireId(), new GridPoint(21, 5),
                new GridPoint(31, 5));
        context.openDocument().document().add(wire);
        // y 5 is one unit off the anchor's bottom (4): parts never pull wires
        assertThat(snapper.snapWires(context, Set.of(wire.id()), 0, 0).winnerY()).isEmpty();
        context.preferences.setSnapToGridLines(true);
        SnapResult onGrid = snapper.snapWires(context, Set.of(wire.id()), 0, 0);
        assertThat(onGrid.winnerX()).map(SnapTarget::kind).contains(SnapKind.GRID_LINE);
        assertThat(onGrid.deltaX() % 2).isNotZero(); // 21 → an even line
        assertThat(onGrid.matches()).isEmpty();
        context.preferences.setGridLineSpacing(5);
        SnapResult onFives = snapper.snapWires(context, Set.of(wire.id()), 0, 0);
        assertThat(onFives.deltaX()).as("21 and 31 one unit past 20 and 30").isEqualTo(-1);
        assertThat(onFives.deltaY()).as("5 already on a line").isZero();
    }
}
