package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class CircuitFormatTest {

    private final CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

    @Test
    void roundTripKeepsEveryElement() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.setName("sumador");
        document.add(new Component(document.newComponentId(), "and2", new GridPoint(10, 4), Rotation.R90,
                Map.of("delay", 3), "U1"));
        document.add(new Component(document.newComponentId(), "switch", new GridPoint(0, 0), Rotation.R0,
                Map.of("value", "Z"), ""));
        document.add(new Wire(document.newWireId(), new GridPoint(0, 0), new GridPoint(4, 0)));
        document.add(new TextLabel(document.newLabelId(), new GridPoint(2, 9), "nota"));

        String json = format.write(document);
        assertThat(json).contains("\"formatVersion\" : 2").contains("\"kind\" : \"circuit\"");

        CircuitDocument loaded = format.read(json);
        assertThat(loaded.name()).isEqualTo("sumador");
        assertThat(Set.copyOf(loaded.elements())).isEqualTo(Set.copyOf(document.elements()));
    }

    @Test
    void missingParametersGetDefaults() throws CircuitFormatException {
        CircuitDocument loaded = format.read("""
                {"formatVersion":2,"kind":"circuit","components":[{"id":"c1","type":"clock","x":1,"y":1}]}
                """);
        assertThat(loaded.components().getFirst().parameters())
                .isEqualTo(Map.of("time-up", 1000, "time-down", 1000));
        assertThat(loaded.wires()).isEmpty();
    }

    @Test
    void nullJsonStringFailsWithInvalidJson() {
        assertThatThrownBy(() -> format.read((String) null))
                .isInstanceOf(CircuitFormatException.class)
                .satisfies(exception -> assertThat(((CircuitFormatException) exception).reason())
                        .isEqualTo(CircuitFormatException.Reason.INVALID_JSON));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> brokenFiles() {
        return Stream.of(
                arguments("{", CircuitFormatException.Reason.INVALID_JSON),
                arguments("{\"formatVersion\":3,\"kind\":\"circuit\"}", CircuitFormatException.Reason.UNSUPPORTED_VERSION),
                arguments("{\"formatVersion\":2,\"kind\":\"component\"}", CircuitFormatException.Reason.INVALID_ELEMENT),
                arguments("{\"formatVersion\":2,\"kind\":\"circuit\",\"components\":[{\"id\":\"c1\",\"type\":\"foo\",\"x\":0,\"y\":0}]}",
                        CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE),
                arguments("{\"formatVersion\":2,\"kind\":\"circuit\",\"components\":[{\"id\":\"c1\",\"type\":\"and2\",\"x\":0,\"y\":0,\"parameters\":{\"delay\":0}}]}",
                        CircuitFormatException.Reason.INVALID_ELEMENT),
                arguments("{\"formatVersion\":2,\"kind\":\"circuit\",\"wires\":[{\"id\":\"w1\",\"x1\":0,\"y1\":0,\"x2\":3,\"y2\":3}]}",
                        CircuitFormatException.Reason.INVALID_ELEMENT),
                arguments("{\"formatVersion\":2,\"kind\":\"circuit\",\"labels\":[{\"id\":\"l1\",\"x\":0,\"y\":0},{\"id\":\"l1\",\"x\":1,\"y\":1}]}",
                        CircuitFormatException.Reason.INVALID_ELEMENT),
                arguments("{\"formatVersion\":2,\"kind\":\"circuit\",\"components\":[{\"type\":\"and2\",\"x\":0,\"y\":0}]}",
                        CircuitFormatException.Reason.INVALID_ELEMENT),
                arguments("{\"formatVersion\":2,\"kind\":\"circuit\",\"components\":[null]}",
                        CircuitFormatException.Reason.INVALID_ELEMENT),
                arguments("{\"formatVersion\":2,\"kind\":\"circuit\",\"wires\":[null]}",
                        CircuitFormatException.Reason.INVALID_ELEMENT),
                arguments("{\"formatVersion\":2,\"kind\":\"circuit\",\"labels\":[null]}",
                        CircuitFormatException.Reason.INVALID_ELEMENT));
    }

    @ParameterizedTest
    @MethodSource("brokenFiles")
    void brokenFilesFailWithAReason(String json, CircuitFormatException.Reason reason) {
        assertThatThrownBy(() -> format.read(json))
                .isInstanceOf(CircuitFormatException.class)
                .satisfies(exception -> assertThat(((CircuitFormatException) exception).reason()).isEqualTo(reason));
    }
}
