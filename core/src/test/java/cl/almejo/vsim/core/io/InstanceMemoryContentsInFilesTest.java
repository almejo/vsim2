package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec §6.6b: a piece's own memory contents are kept in the file, in format 6; older files have none. */
class InstanceMemoryContentsInFilesTest {

    private final CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

    @Test
    void instanceMemoryContentsRoundTrip() throws CircuitFormatException {
        CircuitDocument document = twoSmallMemories();
        document.replace(document.component(new ElementId("c1")).orElseThrow()
                .withMemoryContents(Map.of("c10", "@1 7")));

        String json = format.write(document);
        CircuitDocument read = format.read(json);

        assertThat(json).contains("\"formatVersion\" : 6").contains("\"memoryContents\" : {");
        assertThat(read.component(new ElementId("c1")).orElseThrow().memoryContents()).containsExactly(
                Map.entry("c10", "@1 7"));
        assertThat(read.component(new ElementId("c2")).orElseThrow().memoryContents()).isEmpty();
        assertThat(json.indexOf("memoryContents")).isEqualTo(json.lastIndexOf("memoryContents"));
    }

    @Test
    void aPieceInsideADefinitionKeepsItsContentsToo() throws CircuitFormatException {
        CircuitDocument document = twoSmallMemories();
        Component inner = piece("c3").withMemoryContents(Map.of("c10", "@0 6"));
        document.putDefinition(new ComponentDefinition("cpu", "CPU", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(inner), List.of(), List.of())));
        document.add(new Component(new ElementId("c4"), SubcircuitTypes.typeIdOf("cpu"), new GridPoint(20, 0),
                Rotation.R0, Map.of(), ""));

        CircuitDocument read = format.read(format.write(document));

        assertThat(read.definition("cpu").orElseThrow().body().components().getFirst().memoryContents())
                .containsExactly(Map.entry("c10", "@0 6"));
    }

    @Test
    void anOldFileLoadsWithoutOverrides() throws CircuitFormatException {
        String old = format.write(twoSmallMemories()).replace("\"formatVersion\" : 6", "\"formatVersion\" : 5");

        CircuitDocument read = format.read(old);

        assertThat(read.components()).allSatisfy(component -> assertThat(component.memoryContents()).isEmpty());
    }

    @Test
    void contentsThatCannotBeReadFailTheLoad() {
        String broken = format.write(twoSmallMemories()).replaceFirst("\"label\" : \"\",",
                "\"label\" : \"\", \"memoryContents\" : {\"c10\" : \"@zz 1\"},");

        assertThatThrownBy(() -> format.read(broken)).isInstanceOfSatisfying(CircuitFormatException.class,
                exception -> assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
    }

    @Test
    void contentsOnAPartThatIsNotAPieceFailTheLoad() {
        CircuitDocument document = new CircuitDocument();
        document.add(StandardComponentTypes.registry().require(StandardComponentTypes.RAM).newComponent(
                new ElementId("c1"), GridPoint.ORIGIN, Rotation.R0, Map.of(), ""));
        String broken = format.write(document).replaceFirst("\"label\" : \"\",",
                "\"label\" : \"\", \"memoryContents\" : {\"c10\" : \"@0 1\"},");

        assertThatThrownBy(() -> format.read(broken)).isInstanceOfSatisfying(CircuitFormatException.class,
                exception -> assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
    }

    /** «Memoria chica»: RAM c10 with word 0 = 5; two pieces of it, c1 and c2. */
    private static CircuitDocument twoSmallMemories() {
        CircuitDocument document = new CircuitDocument();
        Component memory = StandardComponentTypes.registry().require(StandardComponentTypes.RAM).newComponent(
                new ElementId("c10"), GridPoint.ORIGIN, Rotation.R0, Map.of(MemoryTypes.CONTENTS, "@0 5"), "");
        document.putDefinition(new ComponentDefinition("memoria-chica", "Memoria chica", "", new GridSize(4, 4),
                List.of(), new CircuitFragment(List.of(memory), List.of(), List.of())));
        document.add(piece("c1"));
        document.add(new Component(new ElementId("c2"), SubcircuitTypes.typeIdOf("memoria-chica"),
                new GridPoint(10, 0), Rotation.R0, Map.of(), ""));
        return document;
    }

    private static Component piece(String id) {
        return new Component(new ElementId(id), SubcircuitTypes.typeIdOf("memoria-chica"), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), "");
    }
}
