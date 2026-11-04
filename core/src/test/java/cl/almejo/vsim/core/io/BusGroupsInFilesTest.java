package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The groups of a «Separador de bus» / «Unión de bus» are optional in files: parts without groups keep their bytes. */
class BusGroupsInFilesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);

    private CircuitDocument documentWith(String typeId, Map<String, Object> parameters) {
        CircuitDocument document = new CircuitDocument();
        document.add(registry.require(typeId).newComponent(new ElementId("c1"), new GridPoint(2, 2), Rotation.R0,
                parameters, ""));
        return document;
    }

    @Test
    void aSplitterWithoutGroupsReadsWithEmptyGroups() throws CircuitFormatException {
        CircuitDocument loaded = format.read("""
                {"formatVersion":2,"kind":"circuit","components":[
                 {"id":"c1","type":"bus-splitter","x":1,"y":1,"parameters":{"width":8}}]}
                """);

        assertThat(loaded.components().getFirst().parameters()).containsEntry(StandardComponentTypes.GROUPS, "");
    }

    @Test
    void emptyGroupsAreLeftOutOfTheFile() throws Exception {
        String json = format.write(documentWith(StandardComponentTypes.BUS_JOINER,
                Map.of(StandardComponentTypes.WIDTH, 8)));

        assertThat(json).doesNotContain("\"" + StandardComponentTypes.GROUPS + "\"");
    }

    @Test
    void groupsAreWrittenAndReadBack() throws Exception {
        String json = format.write(documentWith(StandardComponentTypes.BUS_SPLITTER,
                Map.of(StandardComponentTypes.WIDTH, 32, StandardComponentTypes.GROUPS, "25-31,7-11|0-6")));

        assertThat(json).contains("\"groups\" : \"7-11,25-31|0-6\"");
        Component loaded = format.read(json).components().getFirst();
        assertThat(loaded.parameters()).containsEntry(StandardComponentTypes.GROUPS, "7-11,25-31|0-6");
    }
}
