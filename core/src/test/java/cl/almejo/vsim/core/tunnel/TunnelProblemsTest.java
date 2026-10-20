package cl.almejo.vsim.core.tunnel;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.TunnelProblem.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TunnelProblemsTest {

    private final TunnelCircuit circuit = new TunnelCircuit();

    private List<TunnelProblem> problems() {
        return circuit.simulate().problems().stream()
                .filter(TunnelProblem.class::isInstance)
                .map(TunnelProblem.class::cast)
                .toList();
    }

    private Component switchAt(int x, int y) {
        return circuit.place(StandardComponentTypes.SWITCH, x, y, Map.of());
    }

    private Component ledAt(int x, int y) {
        return circuit.place(StandardComponentTypes.LED, x, y, Map.of());
    }

    @Test
    void aWellFormedTunnelHasNoProblem() {
        Component source = switchAt(0, 0);
        circuit.entrance("CLK", circuit.pin(source, 0));
        Component led = ledAt(40, 20);
        circuit.exit("CLK", circuit.pin(led, 0));

        assertThat(problems()).isEmpty();
    }

    @Test
    void twoEntrancesWithOneNameAreAnErrorThatMarksBoth() {
        Component first = circuit.entrance("CLK", circuit.pin(switchAt(0, 0), 0));
        Component second = circuit.entrance("CLK", circuit.pin(switchAt(0, 10), 0));
        Component exit = circuit.exit("CLK", circuit.pin(ledAt(40, 20), 0));

        assertThat(problems()).singleElement().satisfies(problem -> {
            assertThat(problem.kind()).isEqualTo(Kind.DUPLICATE_ENTRANCE);
            assertThat(problem.name()).isEqualTo("CLK");
            assertThat(problem.tunnels()).containsExactly(first.id().value(), second.id().value());
            assertThat(problem.isWarning()).isFalse();
            assertThat(problem.netId()).isEqualTo(circuit.netOf(exit, 0).id().value());
        });
    }

    @Test
    void anExitWithoutEntranceIsAnError() {
        Component exit = circuit.exit("CLK", circuit.pin(ledAt(40, 20), 0));

        assertThat(problems()).singleElement().satisfies(problem -> {
            assertThat(problem.kind()).isEqualTo(Kind.EXIT_WITHOUT_ENTRANCE);
            assertThat(problem.tunnels()).containsExactly(exit.id().value());
            assertThat(problem.isWarning()).isFalse();
        });
    }

    @Test
    void anEntranceWithoutExitsIsAWarning() {
        Component entrance = circuit.entrance("CLK", circuit.pin(switchAt(0, 0), 0));

        assertThat(problems()).singleElement().satisfies(problem -> {
            assertThat(problem.kind()).isEqualTo(Kind.ENTRANCE_WITHOUT_EXITS);
            assertThat(problem.tunnels()).containsExactly(entrance.id().value());
            assertThat(problem.isWarning()).isTrue();
        });
    }

    @Test
    void anExitWhoseOwnWireHasADriverIsAWarning() {
        circuit.entrance("CLK", circuit.pin(switchAt(0, 0), 0));
        circuit.exit("CLK", circuit.pin(ledAt(40, 20), 0));
        Component wrongSide = circuit.exit("CLK", circuit.pin(switchAt(0, 40), 0));

        assertThat(problems()).singleElement().satisfies(problem -> {
            assertThat(problem.kind()).isEqualTo(Kind.EXIT_DRIVEN);
            assertThat(problem.tunnels()).containsExactly(wrongSide.id().value());
            assertThat(problem.isWarning()).isTrue();
        });
    }

    @Test
    void anExitOnTheWireOfItsOwnEntranceIsNotFlagged() {
        Component source = switchAt(0, 0);
        circuit.entrance("CLK", circuit.pin(source, 0));
        GridPoint farEnd = new GridPoint(circuit.pin(source, 0).x() + 20, circuit.pin(source, 0).y());
        circuit.wire(circuit.pin(source, 0), farEnd);
        circuit.exit("CLK", farEnd);
        circuit.exit("CLK", circuit.pin(ledAt(40, 20), 0));

        assertThat(problems()).isEmpty();
    }

    @Test
    void anExitDrivenThroughAnotherTunnelIsNotFlagged() {
        circuit.entrance("A", circuit.pin(switchAt(0, 0), 0));
        GridPoint shared = new GridPoint(30, 30);
        circuit.exit("A", shared);
        circuit.entrance("B", shared);
        circuit.exit("B", circuit.pin(ledAt(60, 20), 0));

        assertThat(problems()).isEmpty();
    }

    @Test
    void problemsComeAndGoAsTheCircuitIsEdited() {
        Component exit = circuit.exit("CLK", circuit.pin(ledAt(40, 20), 0));
        assertThat(problems()).extracting(TunnelProblem::kind).containsExactly(Kind.EXIT_WITHOUT_ENTRANCE);

        Component entrance = circuit.entrance("CLK", circuit.pin(switchAt(0, 0), 0));
        assertThat(problems()).isEmpty();

        circuit.document().remove(exit.id());
        assertThat(problems()).extracting(TunnelProblem::kind).containsExactly(Kind.ENTRANCE_WITHOUT_EXITS);

        circuit.document().remove(entrance.id());
        assertThat(problems()).isEmpty();
    }

    @Test
    void problemsInsideAComponentAreReportedUnderItsInstance() {
        circuit.document().putDefinition(TunnelSimulationTest.tunnelBuffer("B"));
        Component instance = circuit.place(SubcircuitTypes.typeIdOf(TunnelSimulationTest.TUNNEL_BUFFER), 4, -1,
                Map.of());

        List<TunnelProblem> problems = problems();

        assertThat(problems).extracting(TunnelProblem::kind)
                .containsExactlyInAnyOrder(Kind.ENTRANCE_WITHOUT_EXITS, Kind.EXIT_WITHOUT_ENTRANCE);
        assertThat(problems).allSatisfy(problem -> {
            assertThat(InstancePath.topLevel(problem.netId())).isEqualTo(instance.id().value());
            assertThat(problem.tunnels()).allSatisfy(id -> assertThat(InstancePath.topLevel(id))
                    .isEqualTo(instance.id().value()));
        });
    }
}
