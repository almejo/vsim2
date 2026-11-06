package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.PieceMemories;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.memory.MemoryContents;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §6.6b: setting and clearing one piece's own memory contents, each one undo step. */
class SetInstanceMemoryContentsCommandTest {

    private static final ElementId PIECE = new ElementId("c1");

    private final CircuitDocument document = new CircuitDocument();
    private final History history = new History(document);

    SetInstanceMemoryContentsCommandTest() {
        document.add(new Component(PIECE, SubcircuitTypes.typeIdOf("memoria-chica"), GridPoint.ORIGIN, Rotation.R0,
                Map.of(), "").withMemoryContents(Map.of("c20", "@0 1")));
    }

    @Test
    void settingContentsKeepsThemOnThePieceAndUndoRemovesThem() {
        PieceMemories.Place place = new PieceMemories.Place(PIECE, "c10");

        history.execute(SetInstanceMemoryContentsCommand.set(place, MemoryContents.decode("@1 7")));
        assertThat(piece().memoryContents()).containsOnly(Map.entry("c10", "@1 7"), Map.entry("c20", "@0 1"));

        history.undo();
        assertThat(piece().memoryContents()).containsOnly(Map.entry("c20", "@0 1"));

        history.redo();
        assertThat(piece().memoryContents()).containsEntry("c10", "@1 7");
    }

    @Test
    void clearingGoesBackToTheComponentsContentsAndUndoBringsThemBack() {
        SetInstanceMemoryContentsCommand clear =
                SetInstanceMemoryContentsCommand.clear(new PieceMemories.Place(PIECE, "c20"));

        history.execute(clear);
        assertThat(piece().memoryContents()).isEmpty();
        assertThat(clear.description()).isEqualTo(SetInstanceMemoryContentsCommand.CLEAR_DESCRIPTION);

        history.undo();
        assertThat(piece().memoryContents()).containsOnly(Map.entry("c20", "@0 1"));

        history.redo();
        assertThat(piece().memoryContents()).isEmpty();
    }

    @Test
    void storedContentsAreCanonical() {
        history.execute(SetInstanceMemoryContentsCommand.set(new PieceMemories.Place(PIECE, "c10"),
                MemoryContents.decode("@0 0a 0b")));

        assertThat(piece().memoryContents()).containsEntry("c10", "@0 A B");
    }

    private Component piece() {
        return document.component(PIECE).orElseThrow();
    }
}
