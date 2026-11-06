package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.memory.MemoryContents;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec §6.6b: which contents a memory inside a piece starts with. «Memoria chica» holds RAM c10 (word 0 = 5); «CPU»
 * holds a «Memoria chica» c3 that stores its own word 0 = 6; the circuit has two CPUs, c1 and c2.
 */
class PieceMemoriesTest {

    private static final ElementId MEMORY = new ElementId("c10");

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    PieceMemoriesTest() {
        Component memory = registry.require(StandardComponentTypes.RAM).newComponent(MEMORY, GridPoint.ORIGIN,
                Rotation.R0, Map.of(MemoryTypes.CONTENTS, "@0 5"), "");
        document.putDefinition(definition("memoria-chica", "Memoria chica", memory));
        Component inner = piece("c3", "memoria-chica").withMemoryContents(Map.of("c10", "@0 6"));
        document.putDefinition(definition("cpu", "CPU", inner));
        document.add(piece("c1", "cpu"));
        document.add(piece("c2", "cpu"));
    }

    @Test
    void theOverrideOfAMemoryIsKeptByTheOutermostPieceUnderItsInnerPath() {
        assertThat(PieceMemories.placeOf("c1/c3", MEMORY))
                .isEqualTo(new PieceMemories.Place(new ElementId("c1"), "c3/c10"));
        assertThat(PieceMemories.placeOf("c5", MEMORY)).isEqualTo(new PieceMemories.Place(new ElementId("c5"), "c10"));
    }

    @Test
    void anInnerPieceStoredInADefinitionAppliesWhenNoOuterOneStores() {
        assertThat(PieceMemories.effective(document, "c2/c3/c10")).contains(MemoryContents.decode("@0 6"));
    }

    @Test
    void theOutermostStoredOverrideWins() {
        document.replace(document.component(new ElementId("c1")).orElseThrow()
                .withMemoryContents(Map.of("c3/c10", "@0 7")));

        assertThat(PieceMemories.effective(document, "c1/c3/c10")).contains(MemoryContents.decode("@0 7"));
        assertThat(PieceMemories.stored(document, PieceMemories.placeOf("c1/c3", MEMORY)))
                .contains(MemoryContents.decode("@0 7"));
        assertThat(PieceMemories.stored(document, PieceMemories.placeOf("c2/c3", MEMORY))).isEmpty();
    }

    @Test
    void aMemoryOfTheCircuitOrWithoutOverrideHasNone() {
        assertThat(PieceMemories.effective(document, "c10")).isEmpty();
        assertThat(PieceMemories.effective(document, "c9/c10")).isEmpty();
    }

    @Test
    void aDisassembledPieceGivesItsContentsToItsParts() {
        Component cpu = document.component(new ElementId("c1")).orElseThrow()
                .withMemoryContents(Map.of("c3/c10", "@0 7"));
        CircuitFragment body = document.definition("cpu").orElseThrow().body();

        CircuitFragment pasted = PieceMemories.withContentsOf(cpu, body);

        assertThat(pasted.components().getFirst().memoryContents()).containsEntry("c10", "@0 7");
    }

    @Test
    void aDisassembledPieceWritesItsContentsIntoItsMemory() {
        Component small = piece("c4", "memoria-chica").withMemoryContents(Map.of("c10", "@0 9"));
        CircuitFragment body = document.definition("memoria-chica").orElseThrow().body();

        CircuitFragment pasted = PieceMemories.withContentsOf(small, body);

        assertThat(pasted.components().getFirst().parameters()).containsEntry(MemoryTypes.CONTENTS, "@0 9");
    }

    @Test
    void contentsKeptForAMemoryThatIsNoLongerThereAreFound() {
        // debt of task 9b: an orphan entry was ignored and nothing showed it
        document.replace(document.component(new ElementId("c1")).orElseThrow()
                .withMemoryContents(Map.of("c3/c10", "@0 7", "c3/c99", "@0 8", "c7/c10", "@0 9")));

        assertThat(PieceMemories.storedContentsIssues(document)).containsExactly(
                new PieceMemories.StoredContentsIssue(new PieceMemories.Place(new ElementId("c1"), "c3/c99"),
                        PieceMemories.StoredContentsIssue.Kind.FOR_NOTHING),
                new PieceMemories.StoredContentsIssue(new PieceMemories.Place(new ElementId("c1"), "c7/c10"),
                        PieceMemories.StoredContentsIssue.Kind.FOR_NOTHING));
    }

    private Component piece(String id, String definitionId) {
        return new Component(new ElementId(id), SubcircuitTypes.typeIdOf(definitionId), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), "");
    }

    private static ComponentDefinition definition(String id, String name, Component content) {
        return new ComponentDefinition(id, name, "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(content), List.of(), List.of()));
    }
}
