package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A BOOLEAN parameter travels through a file as a JSON {@code true}/{@code false}. */
class BooleanParameterFormatTest {

    private static final String TYPE_ID = "test-flagged";

    private final ComponentTypeRegistry registry = registryWithAFlaggedType();
    private final CircuitFormat format = new CircuitFormat(registry);

    private static ComponentTypeRegistry registryWithAFlaggedType() {
        ComponentTypeRegistry registry = StandardComponentTypes.registry().copy();
        registry.register(new ComponentType(TYPE_ID, ComponentCategory.GATES, new GridSize(2, 2),
                List.of(PinSpec.output("out", 2, 1)),
                List.of(ParameterSpec.bool("negate", false)),
                parameters -> (context, pinIndex) -> {
                }));
        return registry;
    }

    private static CircuitDocument documentWith(Map<String, Object> parameters) {
        CircuitDocument document = new CircuitDocument();
        document.add(new Component(document.newComponentId(), TYPE_ID, GridPoint.ORIGIN, Rotation.R0, parameters, ""));
        return document;
    }

    @Test
    void aTrueValueIsWrittenAsAJsonBooleanAndReadBack() throws CircuitFormatException {
        String json = format.write(documentWith(Map.of("negate", true)));

        assertThat(json).contains("\"negate\" : true");
        assertThat(format.read(json).components().getFirst().parameters()).containsEntry("negate", true);
    }

    @Test
    void aMissingValueTakesTheDefaultAndAFalseOneRoundTrips() throws CircuitFormatException {
        String json = """
                {"formatVersion":3,"kind":"circuit","components":[
                  {"id":"c1","type":"test-flagged","x":0,"y":0},
                  {"id":"c2","type":"test-flagged","x":10,"y":0,"parameters":{"negate":false}}]}
                """;

        assertThat(format.read(json).components()).extracting(component -> component.parameters().get("negate"))
                .containsExactly(false, false);
    }

    @Test
    void aValueThatIsNotABooleanIsRefused() {
        String json = """
                {"formatVersion":3,"kind":"circuit","components":[
                  {"id":"c1","type":"test-flagged","x":0,"y":0,"parameters":{"negate":"maybe"}}]}
                """;

        assertThatThrownBy(() -> format.read(json)).isInstanceOf(CircuitFormatException.class)
                .satisfies(exception -> assertThat(((CircuitFormatException) exception).reason())
                        .isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
    }
}
