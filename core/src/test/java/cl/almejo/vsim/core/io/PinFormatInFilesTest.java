package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** M7: «Mostrar como» on input and output pins is optional in files, so files written before it keep their bytes. */
class PinFormatInFilesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);

    private Component pin(String typeId, String id, Map<String, Object> parameters) {
        return registry.require(typeId).newComponent(new ElementId(id), new GridPoint(2, 2), Rotation.R0, parameters,
                "A");
    }

    @Test
    void aPinWithoutFormatReadsAsAutomatic() throws CircuitFormatException {
        CircuitDocument loaded = format.read("""
                {"formatVersion":2,"kind":"circuit","components":[
                 {"id":"c1","type":"input-pin","x":1,"y":1,"parameters":{"value":"0","width":8}},
                 {"id":"c2","type":"output-pin","x":5,"y":1,"parameters":{"width":8}}]}
                """);
        for (Component component : loaded.components()) {
            assertThat(component.parameters().get("format")).isEqualTo("auto");
        }
    }

    @Test
    void anAutomaticFormatIsLeftOutOfTheFileAndAnyOtherIsWritten() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(pin(StandardComponentTypes.INPUT_PIN, "c1", Map.of("width", 8)));
        document.add(pin(StandardComponentTypes.OUTPUT_PIN, "c2", Map.of("width", 8)));
        assertThat(format.write(document)).doesNotContain("format\"");

        CircuitDocument signed = new CircuitDocument();
        signed.add(pin(StandardComponentTypes.INPUT_PIN, "c1", Map.of("width", 8, "format", "signed")));
        signed.add(pin(StandardComponentTypes.OUTPUT_PIN, "c2", Map.of("width", 8, "format", "hex")));
        String json = format.write(signed);
        assertThat(json).contains("\"format\" : \"signed\"").contains("\"format\" : \"hex\"");
        CircuitDocument loaded = format.read(json);
        assertThat(loaded.component(new ElementId("c1")).orElseThrow().parameters().get("format"))
                .isEqualTo("signed");
        assertThat(loaded.component(new ElementId("c2")).orElseThrow().parameters().get("format"))
                .isEqualTo("hex");
    }

    @Test
    void aConstantKeepsWritingItsFormatAsBefore() throws CircuitFormatException {
        String written = """
                {
                  "formatVersion" : 2,
                  "kind" : "circuit",
                  "components" : [ {
                    "id" : "c1",
                    "type" : "constant",
                    "x" : 1,
                    "y" : 1,
                    "rotation" : 0,
                    "label" : "",
                    "parameters" : {
                      "format" : "auto",
                      "value" : "0xFF",
                      "width" : 8
                    }
                  } ],
                  "wires" : [ ],
                  "labels" : [ ]
                }""";
        CircuitDocument loaded = format.read(written);
        assertThat(loaded.components().getFirst().parameters())
                .isEqualTo(Map.of("width", 8, "value", "0xFF", "format", "auto"));
        assertThat(format.write(loaded)).contains("\"format\" : \"auto\"").contains("\"value\" : \"0xFF\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"adder-4-bits", "alu-4-bits"})
    void aBundledFileWithPinsIsWrittenBackByteForByte(String name) throws Exception {
        String committed;
        try (InputStream stream = getClass().getResourceAsStream(
                "/cl/almejo/vsim/core/bundled/examples/" + name + ".vsim")) {
            committed = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(committed).contains("input-pin");
        // the bundled resources are written with a final newline (BundledCircuits)
        assertThat(format.write(format.read(committed)) + "\n").isEqualTo(committed);
    }
}
