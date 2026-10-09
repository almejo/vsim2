package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionFormatTest {

    private final DefinitionFormat format = new DefinitionFormat(StandardComponentTypes.registry());
    private final ObjectMapper mapper = new ObjectMapper();
    private final DefinitionBundle doubleBundle = DefinitionBundle.of(ExampleDefinitions.doubleInverter(),
            id -> Optional.of(ExampleDefinitions.inverter()));
    private final DefinitionBundle inverterBundle = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());

    @Test
    void roundTripKeepsTheBundle() throws Exception {
        String json = format.write(doubleBundle);
        JsonNode root = mapper.readTree(json);
        assertThat(root.path("formatVersion").asInt()).isEqualTo(2);
        assertThat(root.path("kind").asText()).isEqualTo("component");
        assertThat(root.path("id").asText()).isEqualTo(ExampleDefinitions.DOUBLE_INVERTER);
        assertThat(root.path("definitions")).hasSize(1);
        assertThat(format.read(json)).isEqualTo(doubleBundle);
        assertThat(mapper.readTree(format.write(inverterBundle)).has("definitions")).isFalse();
        assertThat(DefinitionFormat.FILE_SUFFIX).isEqualTo(".vsimcomponent.json");
    }

    @Test
    void badFilesFailWithAReason() throws Exception {
        String inverter = format.write(inverterBundle);
        assertReason("no json", CircuitFormatException.Reason.INVALID_JSON);
        assertReason("[1, 2]", CircuitFormatException.Reason.INVALID_ELEMENT);
        assertReason(mutated(inverter, root -> root.put("kind", "circuit")), CircuitFormatException.Reason.INVALID_ELEMENT);
        assertReason(mutated(inverter, root -> root.put("formatVersion", 3)),
                CircuitFormatException.Reason.UNSUPPORTED_VERSION);
        assertReason(mutated(format.write(doubleBundle), root -> root.remove("definitions")),
                CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE);
        assertReason(mutated(inverter, root -> ((ObjectNode) root.path("pinout").get(0)).put("source", "c99")),
                CircuitFormatException.Reason.INVALID_ELEMENT);
        assertReason(mutated(inverter, root -> ((ObjectNode) root.path("components").get(1))
                .put("type", "subcircuit:inverter")), CircuitFormatException.Reason.INVALID_ELEMENT);
        assertReason(mutated(inverter, root -> root.put("id", "Not A Slug")), CircuitFormatException.Reason.INVALID_ELEMENT);
        assertReason(mutated(inverter, root -> root.put("name", " ")), CircuitFormatException.Reason.INVALID_ELEMENT);
        assertReason(mutated(inverter, root -> ((ArrayNode) root.path("pinout")).removeAll()),
                CircuitFormatException.Reason.INVALID_ELEMENT);
        assertThatThrownBy(() -> format.read(null)).isInstanceOf(CircuitFormatException.class);
    }

    @Test
    void pinsReadFromAFileAreCheckedLikeBuiltOnes() throws Exception {
        String inverter = format.write(inverterBundle);
        Consumer<ObjectNode> secondPinNamedA = root -> pin(root, 1).put("name", "a");
        Consumer<ObjectNode> blankName = root -> pin(root, 0).put("name", " ");
        Consumer<ObjectNode> samePosition = root -> pin(root, 1).put("x", 0);
        Consumer<ObjectNode> outsideTheBox = root -> pin(root, 1).put("x", 5);
        Consumer<ObjectNode> aboveTheBox = root -> pin(root, 0).put("y", -1);
        Consumer<ObjectNode> wide = root -> pin(root, 0).put("width", 8);
        for (Consumer<ObjectNode> change : List.of(secondPinNamedA, blankName, samePosition, outsideTheBox,
                aboveTheBox, wide)) {
            assertReason(mutated(inverter, change), CircuitFormatException.Reason.INVALID_ELEMENT);
        }
        assertThat(format.read(mutated(inverter, root -> pin(root, 1).put("y", 4)))).isNotNull();
    }

    private static ObjectNode pin(ObjectNode root, int index) {
        return (ObjectNode) root.path("pinout").get(index);
    }

    private String mutated(String json, Consumer<ObjectNode> change) throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(json);
        change.accept(root);
        return root.toString();
    }

    private void assertReason(String json, CircuitFormatException.Reason reason) {
        assertThatThrownBy(() -> format.read(json))
                .isInstanceOf(CircuitFormatException.class)
                .extracting(exception -> ((CircuitFormatException) exception).reason())
                .isEqualTo(reason);
    }

    @Test
    void wideTemplatesRoundTripAndOldFilesReadAsOneBit() throws Exception {
        DefinitionBundle byteBuffer = new DefinitionBundle(ExampleDefinitions.byteBuffer(), List.of());
        String json = format.write(byteBuffer);
        assertThat(pin((ObjectNode) mapper.readTree(json), 0).path("width").asInt()).isEqualTo(8);
        assertThat(format.read(json)).isEqualTo(byteBuffer);

        String withoutWidths = mutated(format.write(inverterBundle), root -> {
            root.path("pinout").forEach(pin -> ((ObjectNode) pin).remove("width"));
            root.path("components").forEach(component -> ((ObjectNode) component.path("parameters")).remove("width"));
        });
        JsonNode tree = mapper.readTree(withoutWidths);
        assertThat(tree.path("pinout").toString() + tree.path("components").toString()).doesNotContain("\"width\"");
        assertThat(format.read(withoutWidths)).isEqualTo(inverterBundle);
    }
}
