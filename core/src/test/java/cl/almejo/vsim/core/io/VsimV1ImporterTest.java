package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VsimV1ImporterTest {

    private final VsimV1Importer importer = new VsimV1Importer(StandardComponentTypes.registry());
    private final ObjectMapper mapper = new ObjectMapper();

    private CircuitDocument importResource(String name) throws IOException, CircuitFormatException {
        try (InputStream input = getClass().getResourceAsStream("/vsim1/" + name)) {
            return importer.importDocument(mapper.readTree(input));
        }
    }

    private CircuitDocument importJson(String json) throws IOException, CircuitFormatException {
        return importer.importDocument(mapper.readTree(json));
    }

    @Test
    void importsOneGateExample() throws Exception {
        CircuitDocument document = importResource("one-gate.json");
        assertThat(document.components()).extracting(Component::typeId).containsExactly("switch", "switch", "xor2");
        Component firstSwitch = document.components().getFirst();
        assertThat(firstSwitch.position()).isEqualTo(new GridPoint(19, 28));
        assertThat(firstSwitch.parameters()).isEqualTo(Map.of("value", "1"));
        assertThat(document.components().get(1).parameters()).isEqualTo(Map.of("value", "0"));
        assertThat(document.wires()).hasSize(4);
    }

    @Test
    void switchTextBecomesLabel() throws Exception {
        CircuitDocument document = importResource("4-bit-adder.json");
        assertThat(document.components()).extracting(Component::label).contains("a0", "b0", "c");
    }

    @Test
    void mapsParametersOfEveryKind() throws Exception {
        CircuitDocument document = importJson("""
                {"gates":[
                  {"type":"clock","position":{"x":0,"y":0},"parameters":{"time-up":200,"time-down":300}},
                  {"type":"switch","position":{"x":0,"y":0},"parameters":{"value":-1}},
                  {"type":"led","position":{"x":0,"y":0},"parameters":{"text":"L1"}},
                  {"type":"not","position":{"x":8,"y":16},"parameters":{"delay":4}},
                  {"type":"time-diagram","position":{"x":0,"y":0},"parameters":{}}
                ],"connections":[{"xi":0,"yi":0,"xf":0,"yf":0}]}
                """);
        assertThat(document.components()).extracting(Component::parameters).containsExactly(
                Map.of("time-up", 200, "time-down", 300),
                Map.of("value", "Z"),
                Map.of("color", "red"),
                Map.of("delay", 4),
                Map.of("sample-interval", 150, "history", 200));
        assertThat(document.components().get(2).label()).isEqualTo("L1");
        assertThat(document.components().get(3).position()).isEqualTo(new GridPoint(1, 2));
        assertThat(document.wires()).isEmpty();
    }

    @Test
    void unknownGateTypeFails() {
        assertThatThrownBy(() -> importJson("{\"gates\":[{\"type\":\"foo\",\"position\":{\"x\":0,\"y\":0}}],\"connections\":[]}"))
                .isInstanceOf(CircuitFormatException.class)
                .satisfies(exception -> assertThat(((CircuitFormatException) exception).reason())
                        .isEqualTo(CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE));
    }

    @Test
    void diagonalConnectionFails() {
        assertThatThrownBy(() -> importJson("{\"gates\":[],\"connections\":[{\"xi\":0,\"yi\":0,\"xf\":8,\"yf\":8}]}"))
                .isInstanceOf(CircuitFormatException.class)
                .satisfies(exception -> assertThat(((CircuitFormatException) exception).reason())
                        .isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
    }
}
