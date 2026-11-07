package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemsReaderTest {

    @TempDir
    Path folder;

    private final ReadFixture fixture = new ReadFixture();

    @Test
    void aMemoryLinkedToAMissingFileIsAWarningNamingItAndItsPath() throws IOException {
        fixture.useCircuitFile(folder.resolve("cpu.vsim"));
        Component rom = fixture.place(StandardComponentTypes.ROM, 0, 0, Rotation.R0,
                Map.of(MemoryTypes.LINK, "programas/rom.hex"), "Programa");

        ProblemsView missing = ProblemsReader.read(fixture.source());

        assertThat(missing.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.kind()).isEqualTo("LINKED_FILE_MISSING");
            assertThat(problem.warning()).isTrue();
            assertThat(problem.elementIds()).containsExactly(rom.id().value());
            assertThat(problem.message()).contains("Programa", "programas/rom.hex");
        });

        Files.createDirectories(folder.resolve("programas"));
        Files.writeString(folder.resolve("programas/rom.hex"), "01\n");
        assertThat(ProblemsReader.read(fixture.source()).problems()).isEmpty();
    }

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

    /** A loose pin of a part is no problem any more (the checks report it apart): the AI never receives one. */
    @Test
    void aLooseInnerPinIsNeverAProblemTheAiReceives() {
        Component gate = fixture.place(StandardComponentTypes.AND, 0, 0, Rotation.R0,
                Map.of(StandardComponentTypes.GATE_INPUTS, 3), "");
        publish(0);

        assertThat(ProblemsReader.read(fixture.source()).problems()).isEmpty();
        publish(0, new ConnectivityProblem("n:pin", gate.id().value(), 2, GridPoint.ORIGIN));
        assertThatThrownBy(() -> ProblemsReader.read(fixture.source()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("never published");
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
    void aLoneTunnelWithoutDirectionIsAWarningThatSaysSo() {
        publish(0, new TunnelProblem("n:5", TunnelProblem.Kind.LONE_TUNNEL, "BUS", List.of("c9")));

        ProblemView problem = ProblemsReader.read(fixture.source()).problems().getFirst();

        assertThat(problem.kind()).isEqualTo("TUNNEL_LONE_TUNNEL");
        assertThat(problem.warning()).isTrue();
        assertThat(problem.message()).contains("\"BUS\"").contains("without direction");
    }

    @Test
    void aSnapshotOfAnOlderCircuitIsNotUpToDate() {
        fixture.place(StandardComponentTypes.BUFFER, 0, 0);
        fixture.useSnapshot(new SimulationSnapshot(0, fixture.document.revision() - 1, 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty()));

        assertThat(ProblemsReader.read(fixture.source()).upToDate()).isFalse();
    }

    @Test
    void anOuterTerminalWhoseInterfaceElementIsUnconnectedInsideHasItsOwnKindThatNamesThePiece() {
        fixture.document.putDefinition(ExampleDefinitions.inverter());
        Component instance = fixture.place(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 10, 10);
        publish(0, new ConnectivityProblem("n:interface", instance.id().value(), 0, GridPoint.ORIGIN,
                ConnectivityProblem.Cause.UNCONNECTED_INTERFACE));

        ProblemView problem = ProblemsReader.read(fixture.source()).problems().getFirst();

        assertThat(problem.kind()).isEqualTo("INTERFACE_UNCONNECTED_INSIDE");
        assertThat(problem.warning()).isTrue();
        assertThat(problem.elementIds()).containsExactly(instance.id().value());
        assertThat(problem.message()).contains(instance.id().value()).contains("inside");
    }

    /**
     * Fix round D (5o): a piece's own memory contents that do not do what they seem — kept over a linked file, or for
     * a memory no longer in its component — are in the Problemas list, so the AI reads them too (never hidden).
     */
    @Test
    void aPieceOwnMemoryContentsThatDoNotApplyAreWarningsNamingThePieceAndTheMemory() {
        Component linkedMemory = fixture.registry.require(StandardComponentTypes.RAM).newComponent(
                new ElementId("c10"), GridPoint.ORIGIN, Rotation.R0, Map.of(MemoryTypes.LINK, "programa.hex"),
                "Programa");
        fixture.document.putDefinition(new ComponentDefinition("memoria-chica", "Memoria chica", "",
                new GridSize(4, 4), List.of(), new CircuitFragment(List.of(linkedMemory), List.of(), List.of())));
        Component piece = fixture.place(SubcircuitTypes.typeIdOf("memoria-chica"), 10, 10, Rotation.R0, Map.of(),
                "Memoria 1");
        fixture.document.replace(piece.withMemoryContents(Map.of("c10", "@0 9", "c99", "@0 8")));
        publish(0);

        List<ProblemView> problems = ProblemsReader.read(fixture.source()).problems().stream()
                .filter(problem -> problem.kind().startsWith("OWN_CONTENTS")).toList();

        String pieceId = piece.id().value();
        assertThat(problems).extracting(ProblemView::kind)
                .containsExactly("OWN_CONTENTS_OVER_LINK", "OWN_CONTENTS_FOR_NOTHING");
        assertThat(problems).extracting(ProblemView::warning).containsExactly(true, true);
        assertThat(problems).allSatisfy(problem -> assertThat(problem.elementIds()).containsExactly(pieceId));
        assertThat(problems.get(0).insidePaths()).containsExactly(pieceId + "/c10");
        assertThat(problems.get(0).netId()).isEqualTo("ownContents:" + pieceId + "/c10");
        assertThat(problems.get(0).message()).contains("Memoria 1", "Programa", pieceId + "/c10", "linked file");
        assertThat(problems.get(1).insidePaths()).isEmpty();
        assertThat(problems.get(1).netId()).isEqualTo("ownContents:" + pieceId + "/c99");
        assertThat(problems.get(1).message()).contains("Memoria 1", "c99", "no longer");
    }

    private void publish(long time, Problem... problems) {
        fixture.useSnapshot(new SimulationSnapshot(0, fixture.document.revision(), time, true, Map.of(), Map.of(),
                List.of(problems), Optional.empty()));
    }
}
