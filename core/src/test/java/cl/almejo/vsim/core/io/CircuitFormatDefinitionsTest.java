package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitFormatDefinitionsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);
    private final ObjectMapper mapper = new ObjectMapper();

    private CircuitDocument documentWithADoubleInverter() {
        CircuitDocument document = new CircuitDocument();
        document.setName("demo");
        document.putDefinition(ExampleDefinitions.doubleInverter());
        document.putDefinition(ExampleDefinitions.inverter());
        document.putDefinition(new ComponentDefinition("spare", "Sobrante", "", ExampleDefinitions.inverter().size(),
                ExampleDefinitions.inverter().pinout(), ExampleDefinitions.inverter().body()));
        document.add(new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER),
                new GridPoint(4, 4), Rotation.R90, Map.of(), "U1"));
        return document;
    }

    @Test
    void savedFilesEmbedTheUsedDefinitionsOnlyDependenciesFirst() throws Exception {
        String json = format.write(documentWithADoubleInverter());
        JsonNode definitions = mapper.readTree(json).path("definitions");
        assertThat(definitions).hasSize(2);
        assertThat(definitions.get(0).path("id").asText()).isEqualTo("inverter");
        assertThat(definitions.get(1).path("id").asText()).isEqualTo("double-inverter");

        CircuitDocument loaded = format.read(json);
        assertThat(loaded.definitions()).containsExactly(ExampleDefinitions.inverter(), ExampleDefinitions.doubleInverter());
        assertThat(loaded.components()).isEqualTo(documentWithADoubleInverter().components());
    }

    @Test
    void definitionsMayComeInAnyOrder() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(format.write(documentWithADoubleInverter()));
        ArrayNode definitions = (ArrayNode) root.path("definitions");
        JsonNode first = definitions.remove(0);
        definitions.add(first);
        CircuitDocument loaded = format.read(root.toString());
        assertThat(loaded.usedDefinitions()).containsExactly(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter());
    }

    @Test
    void aDocumentWithoutInstancesWritesNoDefinitionsKey() {
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(ExampleDefinitions.inverter());
        assertThat(format.write(document)).doesNotContain("definitions");
    }

    @Test
    void badDefinitionsFailWithAReason() throws Exception {
        String valid = format.write(documentWithADoubleInverter());

        ObjectNode missingNested = (ObjectNode) mapper.readTree(valid);
        ((ArrayNode) missingNested.path("definitions")).remove(0);
        assertReason(missingNested, CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE);

        ObjectNode recursive = (ObjectNode) mapper.readTree(valid);
        ((ObjectNode) recursive.path("definitions").get(0).path("components").get(1))
                .put("type", "subcircuit:double-inverter");
        assertReason(recursive, CircuitFormatException.Reason.INVALID_ELEMENT);

        ObjectNode badSource = (ObjectNode) mapper.readTree(valid);
        ((ObjectNode) badSource.path("definitions").get(0).path("pinout").get(0)).put("source", "c99");
        assertReason(badSource, CircuitFormatException.Reason.INVALID_ELEMENT);

        ObjectNode duplicate = (ObjectNode) mapper.readTree(valid);
        ((ArrayNode) duplicate.path("definitions")).add(duplicate.path("definitions").get(0).deepCopy());
        assertReason(duplicate, CircuitFormatException.Reason.INVALID_ELEMENT);

        ObjectNode badDirection = (ObjectNode) mapper.readTree(valid);
        ((ObjectNode) badDirection.path("definitions").get(0).path("pinout").get(0)).put("direction", "SIDEWAYS");
        assertReason(badDirection, CircuitFormatException.Reason.INVALID_ELEMENT);

        ObjectNode unknownInstance = (ObjectNode) mapper.readTree(valid);
        unknownInstance.remove("definitions");
        assertReason(unknownInstance, CircuitFormatException.Reason.UNKNOWN_COMPONENT_TYPE);
    }

    @Test
    void slashInAnElementIdFails() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(format.write(documentWithADoubleInverter()));
        ((ObjectNode) root.path("components").get(0)).put("id", "c1/c2");
        assertReason(root, CircuitFormatException.Reason.INVALID_ELEMENT);
        ObjectNode inBody = (ObjectNode) mapper.readTree(format.write(documentWithADoubleInverter()));
        ((ObjectNode) inBody.path("definitions").get(0).path("components").get(1)).put("id", "c2/x");
        assertReason(inBody, CircuitFormatException.Reason.INVALID_ELEMENT);
    }

    private void assertReason(JsonNode root, CircuitFormatException.Reason reason) {
        assertThatThrownBy(() -> format.read(root.toString()))
                .isInstanceOf(CircuitFormatException.class)
                .extracting(exception -> ((CircuitFormatException) exception).reason())
                .isEqualTo(reason);
    }
}
