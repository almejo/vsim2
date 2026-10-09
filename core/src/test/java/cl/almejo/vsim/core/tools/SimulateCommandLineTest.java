package cl.almejo.vsim.core.tools;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimulateCommandLineTest {

    private final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
    private final PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

    private String output() {
        return outputBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void printsTheTruthTableOfTheVsimFullAdder() {
        int exitCode = SimulateCommandLine.run(new String[]{"src/test/resources/vsim1/4-bit-adder.json"}, out, error);
        assertThat(exitCode).isZero();
        assertThat(output().lines().toList()).containsExactly(
                "a0 b0 c | xor2:c2 or2:c5",
                "0 0 0 | 0 0",
                "0 0 1 | 1 0",
                "0 1 0 | 1 0",
                "0 1 1 | 0 1",
                "1 0 0 | 1 0",
                "1 0 1 | 0 1",
                "1 1 0 | 0 1",
                "1 1 1 | 1 1");
    }

    @Test
    void missingArgumentsPrintUsage() {
        assertThat(SimulateCommandLine.run(new String[]{}, out, error)).isEqualTo(2);
        assertThat(errorBytes.toString(StandardCharsets.UTF_8)).contains("usage");
    }

    @Test
    void unreadableFileIsReported() {
        assertThat(SimulateCommandLine.run(new String[]{"does-not-exist.vsim"}, out, error)).isEqualTo(1);
        assertThat(errorBytes.toString(StandardCharsets.UTF_8)).startsWith("error: READ_ERROR");
    }

    @Test
    void simulatesAFileWhoseInstancesUseItsTemplates(@TempDir Path directory) throws Exception {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(ExampleDefinitions.inverter());
        document.add(registry.require(StandardComponentTypes.SWITCH).newComponent(new ElementId("c1"),
                new GridPoint(0, 0), Rotation.R0, Map.of(), "a"));
        document.add(new Component(new ElementId("c2"), SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER),
                new GridPoint(4, -1), Rotation.R0, Map.of(), "U1"));
        Path file = directory.resolve("template.vsim");
        Files.writeString(file, new CircuitFormat(registry).write(document));

        int exitCode = SimulateCommandLine.run(new String[]{file.toString()}, out, error);

        assertThat(errorBytes.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(exitCode).isZero();
        assertThat(output().lines().toList()).containsExactly("a | U1", "0 | 1", "1 | 0");
    }
}
