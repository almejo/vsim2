package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A RAM's «Flanco» in files: a falling one is not written (old RAMs keep their bytes), a rising one is. */
class RamEdgeInFilesTest {

    private final CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

    private CircuitDocument documentWith(Map<String, Object> parameters) {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "ram", new GridPoint(0, 0), Rotation.R0, parameters,
                ""));
        return document;
    }

    @Test
    void aFallingRamDoesNotWriteItsEdgeAndReadsBackFalling() throws CircuitFormatException {
        String written = format.write(documentWith(Map.of("words", "16")));

        assertThat(written).doesNotContain("\"edge\"");
        assertThat(format.read(written).components().getFirst().parameters()).containsEntry("edge", "falling");
    }

    @Test
    void aRisingRamWritesItsEdgeAndKeepsIt() throws CircuitFormatException {
        String written = format.write(documentWith(Map.of("words", "16", "edge", "rising")));

        assertThat(written).contains("\"edge\" : \"rising\"");
        assertThat(format.read(written).components().getFirst().parameters()).containsEntry("edge", "rising");
    }
}
