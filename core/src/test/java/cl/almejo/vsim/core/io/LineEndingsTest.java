package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saved files are the same bytes on every system: lines end with LF even where the platform's line separator is
 * CRLF (Windows). The writer runs in a child JVM whose {@code line.separator} is CRLF, since Jackson reads the
 * separator once per JVM.
 */
class LineEndingsTest {

    @Test
    void circuitsAndComponentsAreWrittenWithLfEvenWhereTheSystemUsesCrlf(@TempDir Path folder) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(java.toString(), "-Dline.separator=\r\n",
                "-cp", System.getProperty("java.class.path"), Writer.class.getName(), folder.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();

        for (String name : List.of("circuit.vsim", "component.vsimcomponent.json")) {
            String written = Files.readString(folder.resolve(name));
            assertThat(written).as(name).contains("\n").doesNotContain("\r");
        }
    }

    /** The child JVM's main: writes one circuit and one library component into the folder it is given. */
    static final class Writer {
        public static void main(String[] arguments) throws Exception {
            ComponentTypeRegistry registry = StandardComponentTypes.registry();
            Path folder = Path.of(arguments[0]);
            CircuitDocument document = new CircuitDocument();
            document.add(new TextLabel(document.newLabelId(), GridPoint.ORIGIN, "hola"));
            Files.writeString(folder.resolve("circuit.vsim"), new CircuitFormat(registry).write(document));
            Files.writeString(folder.resolve("component.vsimcomponent.json"), new DefinitionFormat(registry).write(
                    new DefinitionBundle(ExampleDefinitions.inverter(), List.of())));
        }
    }
}
