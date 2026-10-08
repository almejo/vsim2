package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitReaderTest {

    private final CircuitReader reader = new CircuitReader(StandardComponentTypes.registry());
    private final CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

    @Test
    void readsVersionTwo() throws Exception {
        CircuitDocument document = new CircuitDocument();
        document.add(new TextLabel(document.newLabelId(), GridPoint.ORIGIN, "hola"));
        assertThat(reader.read(format.write(document)).labels()).hasSize(1);
    }

    @Test
    void readsVersionOne() throws Exception {
        assertThat(reader.read("{\"gates\":[{\"type\":\"not\",\"position\":{\"x\":0,\"y\":0},\"parameters\":{\"delay\":1}}],\"connections\":[]}")
                .components()).hasSize(1);
    }

    @Test
    void rejectsOtherJson() {
        assertThatThrownBy(() -> reader.read("[]"))
                .isInstanceOf(CircuitFormatException.class)
                .satisfies(exception -> assertThat(((CircuitFormatException) exception).reason())
                        .isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
        assertThatThrownBy(() -> reader.read("not json"))
                .isInstanceOf(CircuitFormatException.class)
                .satisfies(exception -> assertThat(((CircuitFormatException) exception).reason())
                        .isEqualTo(CircuitFormatException.Reason.INVALID_JSON));
    }

    @Test
    void readingAFileNamesTheDocumentAfterIt(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("mi-circuito.vsim");
        Files.writeString(file, format.write(new CircuitDocument()));
        assertThat(reader.read(file).name()).isEqualTo("mi-circuito");
    }

    @Test
    void missingFileIsAReadError(@TempDir Path directory) {
        assertThatThrownBy(() -> reader.read(directory.resolve("nope.vsim")))
                .isInstanceOf(CircuitFormatException.class)
                .satisfies(exception -> assertThat(((CircuitFormatException) exception).reason())
                        .isEqualTo(CircuitFormatException.Reason.READ_ERROR));
    }

    @Test
    void nullAndEmptyContentAreInvalidJson() {
        for (String content : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> reader.read(content))
                    .isInstanceOf(CircuitFormatException.class)
                    .satisfies(exception -> assertThat(((CircuitFormatException) exception).reason())
                            .isEqualTo(CircuitFormatException.Reason.INVALID_JSON));
        }
    }

    @Test
    void readingAVersionOneFileNamesTheDocumentAfterIt(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("viejo.json");
        Files.writeString(file, "{\"gates\":[],\"connections\":[]}");
        assertThat(reader.read(file).name()).isEqualTo("viejo");
    }
}
