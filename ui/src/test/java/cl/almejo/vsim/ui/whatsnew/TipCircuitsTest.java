package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.layout.CircuitTidy;
import cl.almejo.vsim.core.layout.CircuitTidyPlan;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.ConnectivityChecks;
import cl.almejo.vsim.core.simulation.ListedProblems;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.wiring.CanvasTextBoxes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tips' circuits are what {@link TipCircuits} builds, and each one shows in the Problems list only what its tip is
 * about (nothing, but the short circuit of «Saltar al próximo problema»).
 */
class TipCircuitsTest {

    private static final Path RESOURCES = Path.of("src/main/resources/tips");
    /** Long enough for every tip circuit to settle (none has a clock). */
    private static final long SETTLING_TIME = 3_000;

    static Stream<String> circuitNames() {
        return TipCircuits.builders().keySet().stream();
    }

    /**
     * Rewrites the circuit files from the builders. Run it after changing a builder, from the repository root:
     * {@code VSIM_WRITE_BUNDLED=1 ./gradlew :ui:test --tests '*TipCircuitsTest.writeTheResources'}.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "VSIM_WRITE_BUNDLED", matches = "1")
    void writeTheResources() throws IOException {
        Files.createDirectories(RESOURCES);
        for (Map.Entry<String, Supplier<CircuitDocument>> builder : TipCircuits.builders().entrySet()) {
            Files.writeString(RESOURCES.resolve(builder.getKey()), written(builder.getValue().get()),
                    StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @MethodSource("circuitNames")
    void theCommittedCircuitIsWhatTheBuilderMakes(String name) throws Exception {
        assertThat(written(TipCircuit.read(name))).isEqualTo(written(TipCircuits.builders().get(name).get()));
    }

    @ParameterizedTest
    @MethodSource("circuitNames")
    void everyCircuitListsOnlyTheProblemsItsTipIsAbout(String name) throws Exception {
        assertThat(listedProblems(TipCircuit.read(name))).as(name)
                .hasSize(TipCircuits.PROBLEMS_SHOWN.getOrDefault(name, 0));
    }

    @Test
    void theNextProblemCircuitHasOneShortCircuitToGoTo() throws Exception {
        assertThat(listedProblems(TipCircuit.read("next-problem.vsim"))).singleElement()
                .isInstanceOf(ShortCircuit.class);
    }

    @Test
    void cleaningTheWiresMovesNoPartAndLeavesFewerWires() throws Exception {
        CircuitDocument before = TipCircuit.read("clean-wires-before.vsim");
        ComponentTypeRegistry registry = registryFor(before);
        Set<ElementId> everything = before.elements().stream().map(DocumentElement::id)
                .collect(Collectors.toUnmodifiableSet());

        CircuitTidyPlan plan = CircuitTidy.plan(before, registry, everything, AppPreferences.inMemory().tidyOptions(),
                new CanvasTextBoxes(registry), () -> false, round -> { }).orElseThrow();

        assertThat(plan.partsMoved()).isZero();
        assertThat(segments(TipCircuit.read("clean-wires-after.vsim"))).isEqualTo(segments(plan.after()))
                .hasSizeLessThan(segments(before).size());
    }

    /** The rows of the Problems list of {@code document} opened alone, after its simulation has settled. */
    private static List<Problem> listedProblems(CircuitDocument document) {
        ComponentTypeRegistry registry = registryFor(document);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationEngine engine = new SimulationEngine();
        engine.update(new CircuitFlattener(registry).flatten(document, netlist));
        engine.advance(SETTLING_TIME);
        SimulationSnapshot snapshot = engine.snapshot();
        assertThat(snapshot.failure()).isEmpty();
        return ListedProblems.of(document, registry, new ListedProblems.Sources(snapshot.problems(),
                ConnectivityChecks.problems(document, registry), Set.of(), List.of(), List.of(), List.of()));
    }

    /** The wires as unordered pairs of end points: ids and drawing direction do not matter. */
    private static Set<Set<GridPoint>> segments(CircuitDocument document) {
        return document.wires().stream().map(wire -> Set.of(wire.start(), wire.end())).collect(Collectors.toSet());
    }

    private static ComponentTypeRegistry registryFor(CircuitDocument document) {
        return StandardComponentTypes.registry().extendedWith(SubcircuitTypes.resolverFor(document));
    }

    private static String written(CircuitDocument document) {
        return new CircuitFormat(registryFor(document)).write(document);
    }
}
