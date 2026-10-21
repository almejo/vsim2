package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Guide;
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

class CircuitFormatGuidesTest {

    private final CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

    @Test
    void guidesRoundTripInOrder() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "switch", new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""));
        List<Guide> guides = List.of(new Guide(Guide.Orientation.VERTICAL, 12),
                new Guide(Guide.Orientation.HORIZONTAL, -6), new Guide(Guide.Orientation.HORIZONTAL, 0));
        document.setGuides(guides);

        String json = format.write(document);
        assertThat(json).contains("\"formatVersion\" : 2").contains("\"guides\"")
                .contains("\"orientation\" : \"vertical\"").contains("\"orientation\" : \"horizontal\"");

        CircuitDocument loaded = format.read(json);
        assertThat(loaded.guides()).containsExactlyElementsOf(guides);
        assertThat(loaded.revision()).isEqualTo(document.revision());
    }

    @Test
    void aCircuitWithoutGuidesWritesNoGuidesKeyAndAFileWithoutThemReadsAsNone() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), "switch", new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""));
        assertThat(format.write(document)).doesNotContain("guides");

        assertThat(format.read("""
                {"formatVersion":2,"kind":"circuit","components":[{"id":"c1","type":"switch","x":0,"y":0}]}
                """).guides()).isEmpty();
        assertThat(format.read("{\"formatVersion\":2,\"kind\":\"circuit\",\"guides\":[]}").guides()).isEmpty();
        assertThat(format.read("{\"formatVersion\":2,\"kind\":\"circuit\",\"guides\":null}").guides()).isEmpty();
    }

    @Test
    void duplicateGuidesInAFileCollapse() throws CircuitFormatException {
        CircuitDocument loaded = format.read("""
                {"formatVersion":2,"kind":"circuit","guides":[
                  {"orientation":"horizontal","position":3},
                  {"orientation":"vertical","position":3},
                  {"orientation":"horizontal","position":3}]}
                """);

        assertThat(loaded.guides()).containsExactly(new Guide(Guide.Orientation.HORIZONTAL, 3),
                new Guide(Guide.Orientation.VERTICAL, 3));
    }

    static Stream<Arguments> brokenGuides() {
        return Stream.of(
                arguments("{}", "guides: must be a list"),
                arguments("[null]", "guides[0]: null entry"),
                arguments("[7]", "guides[0]: not an object"),
                arguments("[{\"position\":1}]", "guides[0]: unknown orientation"),
                arguments("[{\"orientation\":null,\"position\":1}]", "guides[0]: unknown orientation"),
                arguments("[{\"orientation\":\"diagonal\",\"position\":1}]", "guides[0]: unknown orientation diagonal"),
                arguments("[{\"orientation\":\"HORIZONTAL\",\"position\":1}]", "guides[0]: unknown orientation"),
                arguments("[{\"orientation\":\"horizontal\",\"position\":1},{\"orientation\":\"vertical\"}]",
                        "guides[1]: missing position"),
                arguments("[{\"orientation\":\"vertical\",\"position\":null}]", "guides[0]: missing position"),
                arguments("[{\"orientation\":\"vertical\",\"position\":2.5}]", "guides[0]: position must be whole"),
                arguments("[{\"orientation\":\"vertical\",\"position\":2.0}]", "guides[0]: position must be whole"),
                arguments("[{\"orientation\":\"vertical\",\"position\":\"4\"}]", "guides[0]: position must be whole"),
                arguments("[{\"orientation\":\"vertical\",\"position\":3000000000}]",
                        "guides[0]: position out of range"));
    }

    @ParameterizedTest
    @MethodSource("brokenGuides")
    void aMalformedGuideEntryIsAnInvalidElementNamingItsIndex(String guides, String detail) {
        String json = "{\"formatVersion\":2,\"kind\":\"circuit\",\"guides\":" + guides + "}";
        assertThatThrownBy(() -> format.read(json)).isInstanceOfSatisfying(CircuitFormatException.class, exception -> {
            assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT);
            assertThat(exception.detail()).startsWith(detail);
        });
    }
}
