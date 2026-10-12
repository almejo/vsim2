package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The bundled example files must be what {@link BundledCircuits} builds, must open and must run (spec §3.3). */
class BundledCircuitsTest {

    private static final Path RESOURCES = Path.of("src/main/resources", BundledExamples.RESOURCE_DIRECTORY);
    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();

    static Stream<String> exampleIds() {
        return BundledExamples.ids().stream();
    }

    /**
     * Rewrites the resource files from the builders. Run it after changing a builder, from the repository root:
     * {@code VSIM_WRITE_BUNDLED=1 ./gradlew :core:test --tests '*BundledCircuitsTest.writeTheResources'}.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "VSIM_WRITE_BUNDLED", matches = "1")
    void writeTheResources() throws IOException {
        BundledCircuits.main(new String[]{RESOURCES.toString()});
    }

    @Test
    void everyResourceHasABuilderAndTheBuildersFollowTheMenuOrder() throws IOException {
        assertThat(BundledCircuits.builders().keySet()).containsExactlyElementsOf(BundledExamples.ids());
        try (Stream<Path> files = Files.list(RESOURCES)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrderElementsOf(BundledExamples.ids().stream().map(id -> id + ".vsim")
                            .toList());
        }
    }

    @ParameterizedTest
    @MethodSource("exampleIds")
    void theCommittedResourceIsWhatTheBuilderProduces(String id) throws Exception {
        CircuitBuilder builder = BundledCircuits.builders().get(id).get();
        CircuitDocument committed = BundledExamples.open(id, REGISTRY);
        assertThat(new CircuitFormat(REGISTRY).write(committed))
                .isEqualTo(new CircuitFormat(builder.registry()).write(builder.document()));
    }

    @ParameterizedTest
    @MethodSource("exampleIds")
    void everyExampleOpensWithItsTitleAndRunsWithoutProblems(String id) throws Exception {
        CircuitDocument document = BundledExamples.open(id, REGISTRY);
        assertThat(document.name()).isNotBlank()
                .isEqualTo(BundledCircuits.builders().get(id).get().document().name());
        assertThat(document.labels()).anyMatch(label -> label.text().equals(document.name()))
                .anyMatch(label -> label.text().startsWith("Prueba: "));

        ExampleSimulation simulation = new ExampleSimulation(document, Map.of());
        simulation.engine().advance(3000);
        SimulationSnapshot snapshot = simulation.engine().snapshot();
        assertThat(snapshot.failure()).isEmpty();
        assertThat(snapshot.problems()).isEmpty();
    }
}
