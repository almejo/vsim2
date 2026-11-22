package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.ListedProblems;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** A definition whose drawing has a path the codec cannot read still opens, with the default box. */
class UnsupportedDrawingTest {

    private static final String DRAWING = "\"appearance\" : {\"elements\" : [ {\"kind\" : \"path\", "
            + "\"d\" : \"M 0 0 L zz\", \"filled\" : false} ], \"showPinNames\" : true},";

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentDefinition definition = new ComponentDefinition("dibujo", "Dibujo", "", new GridSize(4, 4),
            List.of(), new CircuitFragment(List.of(), List.of(), List.of()));

    private String brokenCircuit() {
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(definition);
        document.add(new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf("dibujo"), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), ""));
        String circuit = new CircuitFormat(registry).write(document);
        String broken = circuit.replaceFirst("\"name\" : \"Dibujo\",", "\"name\" : \"Dibujo\", " + DRAWING);
        assertThat(broken).isNotEqualTo(circuit);
        return broken;
    }

    @Test
    void aCircuitWithABrokenDrawingOpensWithTheDefaultBox(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("roto.vsim.json");
        Files.writeString(file, brokenCircuit());

        CircuitDocument document = new CircuitReader(registry).read(file);

        assertThat(document.definition("dibujo")).isPresent();
        assertThat(document.definition("dibujo").orElseThrow().appearance()).isEmpty();
    }

    @Test
    void theOpenedCircuitListsOneProblemAndNamesTheDefinition(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("roto.vsim.json");
        Files.writeString(file, brokenCircuit());

        CircuitDocument document = new CircuitReader(registry).read(file);

        assertThat(document.unsupportedDrawings()).containsExactly("dibujo");
        assertThat(ListedProblems.of(document, registry, ListedProblems.Sources.published(SimulationSnapshot.EMPTY,
                List.of()))).containsExactly(new ConnectivityProblem("drawing:dibujo", "c1", -1, GridPoint.ORIGIN,
                ConnectivityProblem.Cause.UNSUPPORTED_DRAWING));
    }

    @Test
    void aDefinitionNoPieceUsesIsListedAtTheDocumentLevelBecauseSavingDropsItsDrawing(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("suelto.vsim.json");
        Files.writeString(file, brokenCircuit());
        CircuitDocument document = new CircuitReader(registry).read(file);
        document.remove(new ElementId("c1")); // the last piece that used the definition is gone

        assertThat(ListedProblems.of(document, registry, ListedProblems.Sources.published(SimulationSnapshot.EMPTY,
                List.of()))).containsExactly(new ConnectivityProblem("drawing:dibujo",
                ConnectivityProblem.NO_ELEMENT, -1, GridPoint.ORIGIN, ConnectivityProblem.Cause.UNSUPPORTED_DRAWING));
    }

    @Test
    void savingWritesTheDefinitionWithoutTheBrokenDrawingAndReopensClean(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("roto.vsim.json");
        Files.writeString(file, brokenCircuit());
        CircuitDocument document = new CircuitReader(registry).read(file);

        String saved = new CircuitFormat(registry).write(document);
        Path savedFile = Files.writeString(directory.resolve("guardado.vsim.json"), saved);
        CircuitDocument reopened = new CircuitReader(registry).read(savedFile);

        assertThat(saved).doesNotContain("M 0 0 L zz");
        assertThat(reopened.unsupportedDrawings()).isEmpty();
    }

    @Test
    void aLibraryFileWithABrokenDrawingLoadsWithAProblem(@TempDir Path directory) throws Exception {
        String component = new DefinitionFormat(registry).write(new DefinitionBundle(definition, List.of()));
        String broken = component.replaceFirst("\"name\" : \"Dibujo\",", "\"name\" : \"Dibujo\", " + DRAWING);
        assertThat(broken).isNotEqualTo(component);
        Path library = directory.resolve("library");
        Files.createDirectories(library);
        Path file = library.resolve("dibujo.vsimcomponent.json");
        Files.writeString(file, broken);

        LibraryLoad load = new LibraryDirectory(library, registry).load();

        assertThat(load.bundles()).extracting(bundle -> bundle.root().id()).containsExactly("dibujo");
        assertThat(load.bundles().getFirst().root().appearance()).isEmpty();
        assertThat(load.problems()).containsExactly(new LibraryProblem(file.toAbsolutePath().normalize(),
                LibraryProblem.Kind.UNSUPPORTED_DRAWING, Optional.empty(), "Dibujo"));
        assertThat(Files.readString(file)).isEqualTo(broken);
    }
}
