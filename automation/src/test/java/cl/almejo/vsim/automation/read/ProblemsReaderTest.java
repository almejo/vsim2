package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemsReaderTest {

    private final ReadFixture fixture = new ReadFixture();

    @Test
    void aShortCircuitNamesItsDriversAsPinReferences() {
        Component first = fixture.place(StandardComponentTypes.SWITCH, 0, 0);
        Component second = fixture.place(StandardComponentTypes.SWITCH, 0, 4);
        publish(1234, new ShortCircuit("n:1", List.of(new DriverRef(first.id().value(), 0, Value.ONE),
                new DriverRef(second.id().value(), 0, Value.ZERO)), 1200));

        ProblemsView problems = ProblemsReader.read(fixture.source());

        assertThat(problems.document()).isEqualTo(ReadFixture.ID);
        assertThat(problems.simulationTime()).isEqualTo("1234");
        assertThat(problems.upToDate()).isTrue();
        assertThat(problems.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.kind()).isEqualTo("SHORT_CIRCUIT");
            assertThat(problem.warning()).isFalse();
            assertThat(problem.netId()).isEqualTo("n:1");
            assertThat(problem.elementIds()).containsExactly(first.id().value(), second.id().value());
            assertThat(problem.pins()).containsExactly(new PinReference(first.id().value(), "out", 0),
                    new PinReference(second.id().value(), "out", 0));
            assertThat(problem.insidePaths()).isEmpty();
            assertThat(problem.message()).contains("1200");
        });
    }

    @Test
    void aProblemInsideAUserComponentPointsAtTheInstanceThatHoldsIt() {
        fixture.document.putDefinition(ExampleDefinitions.inverter());
        Component inverter = fixture.place(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 10, 10);
        String inside = inverter.id().value() + "/c2";
        publish(0, new WidthMismatch(inverter.id().value() + "/n:3", List.of(new FlatPinRef(inside, 0),
                new FlatPinRef(inverter.id().value(), 1))));

        ProblemView problem = ProblemsReader.read(fixture.source()).problems().get(0);

        assertThat(problem.kind()).isEqualTo("WIDTH_MISMATCH");
        assertThat(problem.elementIds()).containsExactly(inverter.id().value());
        assertThat(problem.pins()).containsExactly(new PinReference(inverter.id().value(), "y", 0));
        assertThat(problem.insidePaths()).containsExactly(inside);
    }

    @Test
    void theSofterKindsAreWarningsAndEveryKindHasItsName() {
        Component display = fixture.place(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, 0, 0);
        publish(0, new TunnelProblem("n:5", TunnelProblem.Kind.ENTRANCE_WITHOUT_EXITS, "CLK", List.of("c9")),
                new DisplayBusOverride("n:6", display.id().value()));

        List<ProblemView> problems = ProblemsReader.read(fixture.source()).problems();

        assertThat(problems).extracting(ProblemView::kind)
                .containsExactly("TUNNEL_ENTRANCE_WITHOUT_EXITS", "DISPLAY_BUS_OVERRIDE");
        assertThat(problems).extracting(ProblemView::warning).containsExactly(true, true);
        assertThat(problems.get(0).message()).contains("CLK");
        assertThat(problems.get(1).elementIds()).containsExactly(display.id().value());
    }

    @Test
    void aSnapshotOfAnOlderCircuitIsNotUpToDate() {
        fixture.place(StandardComponentTypes.NOT, 0, 0);
        fixture.useSnapshot(new SimulationSnapshot(0, fixture.document.revision() - 1, 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty()));

        assertThat(ProblemsReader.read(fixture.source()).upToDate()).isFalse();
    }

    private void publish(long time, Problem... problems) {
        fixture.useSnapshot(new SimulationSnapshot(0, fixture.document.revision(), time, true, Map.of(), Map.of(),
                List.of(problems), Optional.empty()));
    }
}
