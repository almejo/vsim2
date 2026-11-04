package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A register's «Disparo» in files: a falling one is not written (old registers keep their bytes). */
class RegisterTriggerInFilesTest {

    private final CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

    private CircuitDocument documentWith(Map<String, Object> parameters) {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "register", new GridPoint(0, 0), Rotation.R0,
                parameters, ""));
        return document;
    }

    @Test
    void aFallingRegisterDoesNotWriteItsTriggerAndReadsBackFalling() throws CircuitFormatException {
        String written = format.write(documentWith(Map.of("width", 8)));

        assertThat(written).doesNotContain("\"trigger\"");
        assertThat(format.read(written).components().getFirst().parameters()).containsEntry("trigger", "falling");
    }

    @Test
    void aLatchWritesItsTriggerAndAFileWithItLoadsAsThat() throws CircuitFormatException {
        String written = format.write(documentWith(Map.of("width", 8, "trigger", "high")));

        assertThat(written).contains("\"trigger\" : \"high\"");
        assertThat(format.read(written).components().getFirst().parameters()).containsEntry("trigger", "high");
    }
}
