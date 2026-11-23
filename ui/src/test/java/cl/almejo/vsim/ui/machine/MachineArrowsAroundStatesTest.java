package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** An arrow that would pass behind a third state is drawn curved around it, and can still be clicked. */
class MachineArrowsAroundStatesTest {

    private static final double TOLERANCE = 6;

    /** The middle of a quadratic arrow: a quarter of each end and half of its control point. */
    private static Point2D middleOf(MachineDiagramGeometry.Arrow arrow) {
        return new Point2D.Double(
                0.25 * arrow.start().getX() + 0.5 * arrow.headFrom().getX() + 0.25 * arrow.end().getX(),
                0.25 * arrow.start().getY() + 0.5 * arrow.headFrom().getY() + 0.25 * arrow.end().getY());
    }

    @Test
    void anArrowBehindAThirdStateBendsAwayFromIt() {
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(MachineFixtures.detector());

        MachineDiagramGeometry.Arrow behind = geometry.arrow("t5"); // S2 -> S0 with S1 between them

        assertThat(behind.kind()).isEqualTo(MachineDiagramGeometry.ArrowKind.CURVED);
        assertThat(geometry.arrow("t3").kind()).isEqualTo(MachineDiagramGeometry.ArrowKind.STRAIGHT);
        // its middle clears S1 (centre 160, 0) by more than the state's circle
        assertThat(middleOf(behind).distance(geometry.centre("s1")))
                .isGreaterThan(MachineDiagramGeometry.STATE_RADIUS + 4);
    }

    @Test
    void aClickOnTheBentLineSelectsIt() {
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(MachineFixtures.detector());

        assertThat(geometry.transitionAt(middleOf(geometry.arrow("t5")), TOLERANCE)).contains("t5");
    }

    @Test
    void theHitTestPrefersTheNearestArrowNotTheOneDrawnLast() {
        // two arrows crossing at (150, 0), the vertical one drawn last, far from every other state
        StateMachine machine = new StateMachine(List.of(
                new MachineState("a", "A", new DiagramPoint(0, 0), List.of()),
                new MachineState("b", "B", new DiagramPoint(300, 0), List.of()),
                new MachineState("c", "C", new DiagramPoint(150, -200), List.of()),
                new MachineState("d", "D", new DiagramPoint(150, 200), List.of())),
                List.of(new MachineTransition("across", "a", "b", "", List.of()),
                        new MachineTransition("down", "c", "d", "", List.of())),
                Optional.of("a"), List.of(), List.of(), false, false, StateMachine.EDGE_FALLING, List.of());
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);

        assertThat(geometry.transitionAt(new Point2D.Double(156, 0), 12)).contains("across");
    }
}
