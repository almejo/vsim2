package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class CircuitFormatProbesTest {

    private final CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

    @Test
    void probesRoundTripInOrderWithTheirNamesAndTargets() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "switch", new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""));
        document.add(new Wire(document.newWireId(), new GridPoint(20, 20), new GridPoint(24, 20)));
        List<Probe> probes = List.of(
                new Probe("p2", new ProbeTarget.WireTarget(new ElementId("w2")), "cable w2"),
                new Probe("p1", new ProbeTarget.PinTarget(new ElementId("c1"), 0), "reloj"));
        document.setProbes(probes);

        String json = format.write(document);
        assertThat(json).contains("\"formatVersion\" : 4").contains("\"probes\"")
                .doesNotContain("\"component\" : null").doesNotContain("\"wire\" : null")
                .doesNotContain("\"pin\" : null");

        CircuitDocument loaded = format.read(json);
        assertThat(loaded.probes()).containsExactlyElementsOf(probes);
        assertThat(loaded.newProbeId()).isEqualTo("p3");
        assertThat(loaded.revision()).isEqualTo(document.revision());
    }

    @Test
    void aCircuitWithoutProbesWritesNoProbesKeyAndAnOlderFileReadsAsNoProbes() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "switch", new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""));
        assertThat(format.write(document)).doesNotContain("probes");

        CircuitDocument older = format.read("""
                {"formatVersion":2,"kind":"circuit","components":[{"id":"c1","type":"switch","x":0,"y":0}]}
                """);
        assertThat(older.probes()).isEmpty();
    }

    @Test
    void anEmptyNameIsLeftOutOfTheFileAndReadsBackEmpty() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.setName("contador");
        document.add(new Component(document.newComponentId(), "switch", new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""));
        document.setProbes(List.of(new Probe("p1", new ProbeTarget.PinTarget(new ElementId("c1"), 0), ""),
                new Probe("p2", new ProbeTarget.PinTarget(new ElementId("c1"), 0), "reloj")));

        String json = format.write(document);

        assertThat(json).contains("\"formatVersion\" : 4").contains("\"name\" : \"reloj\"")
                .doesNotContain("\"name\" : \"\"").doesNotContain("\"name\" : null");
        assertThat(format.read(json).probes()).containsExactlyElementsOf(document.probes());
    }

    @Test
    void probesOnElementsThatDoNotExistAreKept() throws CircuitFormatException {
        CircuitDocument loaded = format.read("""
                {"formatVersion":2,"kind":"circuit","probes":[
                  {"id":"p1","name":"perdido","component":"c9","pin":3},
                  {"id":"p2","wire":"w9"}]}
                """);
        assertThat(loaded.probes()).containsExactly(
                new Probe("p1", new ProbeTarget.PinTarget(new ElementId("c9"), 3), "perdido"),
                new Probe("p2", new ProbeTarget.WireTarget(new ElementId("w9")), ""));
    }

    static Stream<Arguments> brokenProbes() {
        return Stream.of(
                arguments("[null]", "probes[0]: null entry"),
                arguments("[{\"wire\":\"w1\"}]", "probes[0]: missing id"),
                arguments("[{\"id\":\"p1\",\"name\":\"x\"}]", "probes[0]: a probe needs"),
                arguments("[{\"id\":\"p1\",\"component\":\"c1\"}]", "probes[0]: a probe needs"),
                arguments("[{\"id\":\"p1\",\"component\":\"c1\",\"pin\":0,\"wire\":\"w1\"}]", "probes[0]: a probe needs"),
                arguments("[{\"id\":\"p1\",\"component\":\"c1\",\"pin\":-1}]", "probes[0]: negative pin -1"),
                arguments("[{\"id\":\"p1\",\"wire\":\"w1\"},{\"id\":\"p1\",\"wire\":\"w2\"}]",
                        "probes[1]: duplicate id p1"),
                arguments("[{\"id\":\"p1\",\"wire\":\"a/b\"}]", "probes[0]: element id must not contain"));
    }

    @ParameterizedTest
    @MethodSource("brokenProbes")
    void aMalformedProbeEntryIsAnInvalidElementNamingItsIndex(String probes, String detail) {
        String json = "{\"formatVersion\":2,\"kind\":\"circuit\",\"probes\":" + probes + "}";
        assertThatThrownBy(() -> format.read(json)).isInstanceOfSatisfying(CircuitFormatException.class, exception -> {
            assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT);
            assertThat(exception.detail()).startsWith(detail);
        });
    }
}
