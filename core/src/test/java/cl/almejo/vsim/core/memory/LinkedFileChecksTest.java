package cl.almejo.vsim.core.memory;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.LinkedFileProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedFileChecksTest {

    @TempDir
    Path folder;

    private final CircuitDocument document = new CircuitDocument();

    private Component memory(String typeId, String link) {
        Map<String, Object> parameters = new HashMap<>();
        if (link != null) {
            parameters.put(MemoryTypes.LINK, link);
        }
        Component component = StandardComponentTypes.registry().require(typeId)
                .newComponent(document.newComponentId(), new GridPoint(0, 0), Rotation.R0, parameters, "Programa");
        document.add(component);
        return component;
    }

    @Test
    void aMemoryLinkedToAMissingFileHasOneProblemWithItsPath() {
        Component rom = memory(StandardComponentTypes.ROM, "programas/rom.hex");
        Optional<Path> circuit = Optional.of(folder.resolve("cpu.vsim"));

        List<LinkedFileProblem> problems = LinkedFileChecks.missing(document, circuit);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.elementId()).isEqualTo(rom.id().value());
            assertThat(problem.link()).isEqualTo("programas/rom.hex");
            assertThat(problem.kind()).isEqualTo(LinkedFileProblem.Kind.MISSING);
        });
    }

    @Test
    void aPresentFileOrNoLinkOrAnotherPartHasNoProblem() throws IOException {
        Files.writeString(folder.resolve("rom.hex"), "01\n");
        memory(StandardComponentTypes.ROM, "rom.hex");
        memory(StandardComponentTypes.RAM, null);
        memory(StandardComponentTypes.RAM, "   ");
        memory(StandardComponentTypes.BUFFER, "nothing.hex");

        assertThat(LinkedFileChecks.missing(document, Optional.of(folder.resolve("cpu.vsim")))).isEmpty();
    }

    @Test
    void theFileComingBackOrTheLinkGoingClearsIt() throws IOException {
        Component ram = memory(StandardComponentTypes.RAM, "ram.hex");
        Optional<Path> circuit = Optional.of(folder.resolve("cpu.vsim"));
        assertThat(LinkedFileChecks.missing(document, circuit)).hasSize(1);

        Files.writeString(folder.resolve("ram.hex"), "01\n");
        assertThat(LinkedFileChecks.missing(document, circuit)).isEmpty();

        Files.delete(folder.resolve("ram.hex"));
        Map<String, Object> parameters = new HashMap<>(ram.parameters());
        parameters.remove(MemoryTypes.LINK);
        document.replace(ram.withParameters(parameters));
        assertThat(LinkedFileChecks.missing(document, circuit)).isEmpty();
    }
}
