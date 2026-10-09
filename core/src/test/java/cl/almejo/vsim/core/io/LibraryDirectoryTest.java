package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class LibraryDirectoryTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final DefinitionBundle inverter = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
    private final DefinitionBundle doubleInverter = DefinitionBundle.of(ExampleDefinitions.doubleInverter(),
            id -> Optional.of(ExampleDefinitions.inverter()));

    @Test
    void writtenComponentsLoadBackWithoutLeftovers(@TempDir Path directory) throws Exception {
        Path components = directory.resolve("components");
        LibraryDirectory library = new LibraryDirectory(components, registry);
        assertThat(library.load()).isEqualTo(new LibraryLoad(List.of(), List.of()));

        library.write(inverter);
        library.write(doubleInverter);

        try (Stream<Path> files = Files.list(components)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder("inverter.vsimcomponent.json", "double-inverter.vsimcomponent.json");
        }
        LibraryLoad load = library.load();
        assertThat(load.bundles()).containsExactlyInAnyOrder(inverter, doubleInverter);
        assertThat(load.problems()).isEmpty();
    }

    @Test
    void brokenFilesAreSkippedAndListed(@TempDir Path directory) throws Exception {
        LibraryDirectory library = new LibraryDirectory(directory, registry);
        library.write(inverter);
        String inverterJson = Files.readString(directory.resolve("inverter.vsimcomponent.json"));
        Files.writeString(directory.resolve("a-copy.vsimcomponent.json"), inverterJson);
        Files.writeString(directory.resolve("broken.vsimcomponent.json"), "{ not json");
        ObjectNode nested = (ObjectNode) new ObjectMapper().readTree(new DefinitionFormat(registry).write(doubleInverter));
        nested.remove("definitions");
        Files.writeString(directory.resolve("nested.vsimcomponent.json"), nested.toString());
        Files.createDirectory(directory.resolve("folder.vsimcomponent.json"));
        Files.writeString(directory.resolve("notes.txt"), "not a component");

        LibraryLoad load = library.load();

        assertThat(load.bundles()).containsExactly(inverter);
        assertThat(load.problems())
                .extracting(problem -> problem.file().getFileName().toString(), LibraryProblem::kind)
                .containsExactly(
                        tuple("a-copy.vsimcomponent.json", LibraryProblem.Kind.DUPLICATE_ID),
                        tuple("broken.vsimcomponent.json", LibraryProblem.Kind.INVALID),
                        tuple("folder.vsimcomponent.json", LibraryProblem.Kind.UNREADABLE),
                        tuple("nested.vsimcomponent.json", LibraryProblem.Kind.INVALID));
        assertThat(load.problems().get(0).detail()).isEqualTo(ExampleDefinitions.INVERTER);
        assertThat(load.problems().get(1).reason()).contains(CircuitFormatException.Reason.INVALID_JSON);
        assertThat(load.problems().get(3).reason()).contains(CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE);
    }

    @Test
    void deleteFindsTheFileThatHoldsTheIdEvenUnderAnotherName(@TempDir Path directory) throws Exception {
        LibraryDirectory library = new LibraryDirectory(directory, registry);
        Path renamedByHand = directory.resolve("my-gate.vsimcomponent.json");
        Files.writeString(renamedByHand, new DefinitionFormat(registry).write(inverter));

        assertThat(library.locate(ExampleDefinitions.INVERTER)).contains(renamedByHand);
        assertThat(library.delete(ExampleDefinitions.INVERTER)).isTrue();
        assertThat(library.delete(ExampleDefinitions.INVERTER)).isFalse();
        assertThat(renamedByHand).doesNotExist();
        assertThat(library.load().bundles()).isEmpty();
    }

    @Test
    void renameRewritesTheSameFileAndExportWritesACopy(@TempDir Path directory) throws Exception {
        LibraryDirectory library = new LibraryDirectory(directory.resolve("library"), registry);
        library.write(inverter);

        DefinitionBundle renamed = library.rename(inverter, "Negador");

        assertThat(renamed.root().name()).isEqualTo("Negador");
        assertThat(renamed.root().id()).isEqualTo(ExampleDefinitions.INVERTER);
        assertThat(library.load().bundles()).containsExactly(renamed);
        Path exported = directory.resolve("out").resolve("negador.vsimcomponent.json");
        Files.createDirectories(exported.getParent());
        library.export(renamed, exported);
        assertThat(library.read(exported)).isEqualTo(renamed);
        try (Stream<Path> files = Files.list(directory.resolve("library"))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("inverter.vsimcomponent.json");
        }
    }

    @Test
    void aFileNamedAfterAnIdButHoldingAnotherComponentIsNeitherFoundNorOverwritten(@TempDir Path directory)
            throws Exception {
        LibraryDirectory library = new LibraryDirectory(directory, registry);
        Path misnamed = library.fileOf(ExampleDefinitions.INVERTER);
        String doubleInverterJson = new DefinitionFormat(registry).write(doubleInverter);
        Files.writeString(misnamed, doubleInverterJson);

        assertThat(library.locate(ExampleDefinitions.INVERTER)).isEmpty();
        assertThat(library.delete(ExampleDefinitions.INVERTER)).isFalse();
        Path written = library.write(inverter);

        assertThat(written).isEqualTo(directory.resolve("inverter-2.vsimcomponent.json"));
        assertThat(Files.readString(misnamed)).isEqualTo(doubleInverterJson);
        assertThat(library.locate(ExampleDefinitions.INVERTER)).contains(written);
        assertThat(library.locate(ExampleDefinitions.DOUBLE_INVERTER)).contains(misnamed);
        assertThat(library.load().bundles()).containsExactlyInAnyOrder(inverter, doubleInverter);
    }
}
