package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.paint.MachineFaceLayout.ArrowKind;
import cl.almejo.vsim.ui.paint.MachineFaceLayout.FaceArrow;
import cl.almejo.vsim.ui.paint.MachineFaceLayout.FaceLayout;
import cl.almejo.vsim.ui.paint.MachineFaceLayout.FaceState;
import cl.almejo.vsim.ui.paint.MachineFaceLayout.Mode;
import org.junit.jupiter.api.Test;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static cl.almejo.vsim.ui.paint.FaceMachines.arrow;
import static cl.almejo.vsim.ui.paint.FaceMachines.loopsOnEveryState;
import static cl.almejo.vsim.ui.paint.FaceMachines.withStates;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The fixed places of a machine's miniature on its face (mockup D) and the switch to the big name from 5 states. */
class MachineFaceLayoutTest {

    private static final Rectangle2D AREA = new Rectangle2D.Double(20, 14, 60, 40);
    private static final double TOLERANCE = 0.001;

    private static List<Point2D> centres(FaceLayout layout) {
        return layout.states().stream().map(FaceState::centre).toList();
    }

    private static FaceArrow arrowOf(FaceLayout layout, String transitionId) {
        return layout.arrows().stream().filter(arrow -> arrow.transitionId().equals(transitionId)).findFirst()
                .orElseThrow();
    }

    @Test
    void oneStateSitsInTheCentre() {
        FaceLayout layout = MachineFaceLayout.of(withStates(1), AREA);

        assertThat(layout.mode()).isEqualTo(Mode.MINIATURE);
        assertThat(centres(layout)).containsExactly(new Point2D.Double(AREA.getCenterX(), AREA.getCenterY()));
    }

    @Test
    void twoStatesGoLeftAndRight() {
        List<Point2D> centres = centres(MachineFaceLayout.of(withStates(2), AREA));

        assertThat(centres).hasSize(2);
        assertThat(centres.get(0).getX()).isLessThan(AREA.getCenterX());
        assertThat(centres.get(1).getX()).isGreaterThan(AREA.getCenterX());
        assertThat(centres.get(0).getY()).isCloseTo(AREA.getCenterY(), within(TOLERANCE));
        assertThat(centres.get(1).getY()).isCloseTo(AREA.getCenterY(), within(TOLERANCE));
    }

    @Test
    void threeStatesMakeATriangleWithTheFirstOnTop() {
        List<Point2D> centres = centres(MachineFaceLayout.of(withStates(3), AREA));

        assertThat(centres.get(0).getX()).isCloseTo(AREA.getCenterX(), within(TOLERANCE));
        assertThat(centres.get(0).getY()).isLessThan(centres.get(1).getY());
        assertThat(centres.get(1).getY()).isCloseTo(centres.get(2).getY(), within(TOLERANCE));
        assertThat(centres.get(1).getX()).as("clockwise: the second one bottom right")
                .isGreaterThan(AREA.getCenterX());
        assertThat(centres.get(2).getX()).isLessThan(AREA.getCenterX());
    }

    @Test
    void fourStatesMakeASquareClockwiseFromTheTopLeft() {
        List<Point2D> centres = centres(MachineFaceLayout.of(withStates(4), AREA));

        Point2D topLeft = centres.get(0);
        Point2D topRight = centres.get(1);
        Point2D bottomRight = centres.get(2);
        Point2D bottomLeft = centres.get(3);
        assertThat(topLeft.getY()).isCloseTo(topRight.getY(), within(TOLERANCE)).isLessThan(bottomRight.getY());
        assertThat(bottomRight.getY()).isCloseTo(bottomLeft.getY(), within(TOLERANCE));
        assertThat(topLeft.getX()).isCloseTo(bottomLeft.getX(), within(TOLERANCE)).isLessThan(topRight.getX());
        assertThat(topRight.getX()).isCloseTo(bottomRight.getX(), within(TOLERANCE));
    }

    @Test
    void fromFiveStatesTheFaceShowsTheBigNameAndNoMiniature() {
        FaceLayout layout = MachineFaceLayout.of(withStates(5), AREA);

        assertThat(layout.mode()).isEqualTo(Mode.BIG_NAME);
        assertThat(layout.states()).isEmpty();
        assertThat(layout.arrows()).isEmpty();
    }

    @Test
    void aMachineWithNoStatesIsAnEmptyMiniature() {
        FaceLayout layout = MachineFaceLayout.of(withStates(0), AREA);

        assertThat(layout.mode()).isEqualTo(Mode.MINIATURE);
        assertThat(layout.states()).isEmpty();
    }

    @Test
    void anArrowIsStraightCurvedWhenTheOtherWayAlsoHasOneOrASelfLoop() {
        StateMachine machine = withStates(3, arrow("t1", "s0", "s1"), arrow("t2", "s1", "s0"),
                arrow("t3", "s1", "s2"), arrow("t4", "s2", "s2"));

        FaceLayout layout = MachineFaceLayout.of(machine, AREA);

        assertThat(arrowOf(layout, "t1").kind()).isEqualTo(ArrowKind.CURVED);
        assertThat(arrowOf(layout, "t2").kind()).isEqualTo(ArrowKind.CURVED);
        assertThat(arrowOf(layout, "t3").kind()).isEqualTo(ArrowKind.STRAIGHT);
        assertThat(arrowOf(layout, "t4").kind()).isEqualTo(ArrowKind.SELF_LOOP);
    }

    @Test
    void theTwoWaysOfAPairBendToDifferentSides() {
        StateMachine machine = withStates(2, arrow("t1", "s0", "s1"), arrow("t2", "s1", "s0"));

        FaceLayout layout = MachineFaceLayout.of(machine, AREA);

        Rectangle2D going = arrowOf(layout, "t1").path().getBounds2D();
        Rectangle2D coming = arrowOf(layout, "t2").path().getBounds2D();
        assertThat(going.getCenterY()).isNotCloseTo(coming.getCenterY(), within(1.0));
    }

    @Test
    void twoArrowsTheSameWayDoNotHideEachOther() {
        StateMachine machine = withStates(2, arrow("t1", "s0", "s1"), arrow("t2", "s0", "s1"));

        FaceLayout layout = MachineFaceLayout.of(machine, AREA);

        assertThat(arrowOf(layout, "t1").path().getBounds2D())
                .isNotEqualTo(arrowOf(layout, "t2").path().getBounds2D());
    }

    @Test
    void everyArrowEndsOnTheBorderOfItsTargetState() {
        StateMachine machine = withStates(4, arrow("t1", "s0", "s1"), arrow("t2", "s0", "s2"),
                arrow("t3", "s3", "s3"), arrow("t4", "s2", "s1"), arrow("t5", "s1", "s2"));

        FaceLayout layout = MachineFaceLayout.of(machine, AREA);

        assertThat(layout.arrows()).hasSize(5);
        for (FaceArrow faceArrow : layout.arrows()) {
            String targetId = machine.transition(faceArrow.transitionId()).orElseThrow().toId();
            FaceState target = layout.states().stream().filter(state -> state.stateId().equals(targetId))
                    .findFirst().orElseThrow();
            assertThat(faceArrow.tip().distance(target.centre())).as(faceArrow.transitionId())
                    .isCloseTo(target.radius(), within(0.5));
        }
    }

    @Test
    void theMiniatureStaysInsideItsArea() {
        for (int stateCount = 1; stateCount <= MachineFaceLayout.MINIATURE_LIMIT; stateCount++) {
            StateMachine machine = withStates(stateCount, loopsOnEveryState(stateCount));

            FaceLayout layout = MachineFaceLayout.of(machine, AREA);

            for (FaceState state : layout.states()) {
                assertThat(AREA.contains(state.circle().getBounds2D())).as(stateCount + " states").isTrue();
            }
            for (FaceArrow faceArrow : layout.arrows()) {
                // a Path2D's bounds are tight (a curve's own may include its control points)
                assertThat(AREA.contains(new Path2D.Double(faceArrow.path()).getBounds2D()))
                        .as(stateCount + " states, " + faceArrow.transitionId()).isTrue();
            }
        }
    }
}
