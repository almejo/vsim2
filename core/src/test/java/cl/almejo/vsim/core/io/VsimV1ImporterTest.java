package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        assertThat(document.components()).extracting(Component::typeId).containsExactly("switch", "switch", "xor");
        Component firstSwitch = document.components().getFirst();
        assertThat(firstSwitch.position()).isEqualTo(new GridPoint(19, 28));
        assertThat(firstSwitch.parameters()).isEqualTo(Map.of("value", "1", "width", 1, "side", "right"));
        assertThat(document.components().get(1).parameters()).isEqualTo(Map.of("value", "0", "width", 1, "side", "right"));
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
                Map.of("value", "Z", "width", 1, "side", "right"),
                Map.of("color", "red", "width", 1, "side", "left"),
                Map.of("width", 1, "negate", true, "delay", 4),
                Map.of("sample-interval", 150, "history", 200));
        assertThat(document.components().get(2).label()).isEqualTo("L1");
        assertThat(document.components().get(3).position()).isEqualTo(new GridPoint(1, 2));
        assertThat(document.wires()).isEmpty();
    }

    @Test
    void aVsim1DFlipFlopKeepsItsThreePins() throws Exception {
        Component flipFlop = importJson("""
                {"gates":[{"type":"flip-flop-data","position":{"x":8,"y":16},"parameters":{"delay":3}}],
                 "connections":[]}
                """).components().getFirst();

        assertThat(flipFlop.parameters()).containsEntry(StandardComponentTypes.SHOW_INVERTED, false);
    }

    @ParameterizedTest(name = "{0} → {1}, {2} inputs, negated {3}")
    @CsvSource({"and2,and,2,false", "and3,and,3,false", "and4,and,4,false", "or2,or,2,false", "or3,or,3,false",
            "or4,or,4,false", "xor2,xor,2,false", "xor3,xor,3,false", "xor4,xor,4,false", "nand2,and,2,true",
            "nand3,and,3,true", "nand4,and,4,true", "nor2,or,2,true", "nor3,or,3,true", "nor4,or,4,true"})
    void everyVsim1GateBecomesTheParameterizedGate(String vsim1Id, String typeId, int inputs, boolean negated)
            throws Exception {
        Component gate = importJson("""
                {"gates":[{"type":"%s","position":{"x":8,"y":16},"parameters":{"delay":3}}],"connections":[]}
                """.formatted(vsim1Id)).components().getFirst();

        assertThat(gate.typeId()).isEqualTo(typeId);
        assertThat(gate.parameters()).isEqualTo(Map.of("inputs", inputs, "width", 1, "negate", negated, "delay", 3));
    }

    @Test
    void theVsim1NotBecomesANegatedBuffer() throws Exception {
        Component gate = importJson("""
                {"gates":[{"type":"not","position":{"x":8,"y":16},"parameters":{}}],"connections":[]}
                """).components().getFirst();

        assertThat(gate.typeId()).isEqualTo("buffer");
        assertThat(gate.parameters()).containsEntry("negate", true);
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

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> importJson(json))
                .isInstanceOf(CircuitFormatException.class)
                .satisfies(exception -> assertThat(((CircuitFormatException) exception).reason())
                        .isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
    }

    @Test
    void gateWithoutPositionFails() {
        assertInvalid("{\"gates\":[{\"type\":\"not\"}],\"connections\":[]}");
    }

    @Test
    void gateWithoutTypeFails() {
        assertInvalid("{\"gates\":[{\"position\":{\"x\":0,\"y\":0}}],\"connections\":[]}");
    }

    @Test
    void connectionMissingACoordinateFails() {
        assertInvalid("{\"gates\":[],\"connections\":[{\"xi\":0,\"yi\":0,\"xf\":8}]}");
    }

    @Test
    void connectionsMustBeAnArray() {
        assertInvalid("{\"gates\":[],\"connections\":{}}");
    }

    @Test
    void outOfRangeParameterFails() {
        assertInvalid("{\"gates\":[{\"type\":\"not\",\"position\":{\"x\":0,\"y\":0},\"parameters\":{\"delay\":0}}],\"connections\":[]}");
    }

    @Test
    void nullTextGivesEmptyLabelAndTextOnOtherGatesIsIgnored() throws Exception {
        CircuitDocument document = importJson("""
                {"gates":[
                  {"type":"led","position":{"x":0,"y":0},"parameters":{"text":null}},
                  {"type":"not","position":{"x":0,"y":0},"parameters":{"text":"ignored"}}
                ],"connections":[]}
                """);
        assertThat(document.components()).extracting(Component::label).containsExactly("", "");
    }
}
