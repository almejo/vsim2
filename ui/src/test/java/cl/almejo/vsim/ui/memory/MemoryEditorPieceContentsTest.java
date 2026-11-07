package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec §6.6b, the user's hand test on {@code pestanas-banco.vsim}: inside «Memoria chica» with «Valores de: Memoria
 * 1», writing a word changes Memoria 1 only. «Memoria chica» holds RAM c3 (word 0 = 5); its pieces are «Memoria 1»
 * (c5) and «Memoria 2» (c9).
 */
class MemoryEditorPieceContentsTest {

    private static final ElementId MEMORY = new ElementId("c3");
    private static final ElementId FIRST = new ElementId("c5");
    private static final ElementId SECOND = new ElementId("c9");

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<OpenDocument> bodies = new ArrayList<>();

    @TempDir
    Path folder;

    MemoryEditorPieceContentsTest() {
        Component memory = StandardComponentTypes.registry().require(StandardComponentTypes.RAM).newComponent(MEMORY,
                GridPoint.ORIGIN, Rotation.R0, Map.of(MemoryTypes.CONTENTS, "@0 5"), "");
        circuit.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition("memoria-chica", "Memoria chica",
                "", new GridSize(4, 4), List.of(), new CircuitFragment(List.of(memory), List.of(), List.of())))));
        piece(FIRST, 0, "Memoria 1");
        piece(SECOND, 20, "Memoria 2");
        circuit.history().clear();
    }

    @AfterEach
    void closeAll() {
        bodies.forEach(OpenDocument::close);
        circuit.close();
    }

    @Test
    void insideATabItEditsTheContextPiece() {
        MemoryEditorDialog dialog = editorInside(Optional.of("c5"));
        dialog.model().setCell(1, "7");

        assertThat(dialog.accept()).isTrue();

        assertThat(piece(FIRST).memoryContents()).containsEntry("c3", "@0 5 7");
        assertThat(piece(SECOND).memoryContents()).isEmpty();
        assertThat(definitionContents()).isEqualTo("@0 5");
        assertThat(dialog.title()).isEqualTo("Contenido de RAM1 de Memoria chica — solo Memoria 1 · RAM · 256 × 8");
        assertThat(circuit.history().undoDescription()).contains(
                cl.almejo.vsim.core.command.SetInstanceMemoryContentsCommand.SET_DESCRIPTION);
    }

    @Test
    void theOtherPieceKeepsItsWordsInTheSimulation() {
        MemoryEditorDialog dialog = editorInside(Optional.of("c5"));
        dialog.model().setCell(1, "7");
        dialog.accept();
        awaitSimulation(circuit);

        OpenDocument first = body(Optional.of("c5"));
        OpenDocument second = body(Optional.of("c9"));
        assertThat(first.requestMemoryContents(MEMORY).join()).hasValueSatisfying(contents ->
                assertThat(contents.word(1)).isEqualTo(7));
        assertThat(second.requestMemoryContents(MEMORY).join()).hasValueSatisfying(contents ->
                assertThat(contents.word(1)).isZero());
    }

    @Test
    void theEditorOfAPieceShowsItsOwnContents() {
        MemoryEditorDialog first = editorInside(Optional.of("c5"));
        first.model().setCell(1, "7");
        first.accept();

        assertThat(editorInside(Optional.of("c5")).model().valueAt(1)).isEqualTo(7);
        assertThat(editorInside(Optional.of("c9")).model().valueAt(1)).isZero();
    }

    @Test
    void undoOfAPieceEditGivesItBackTheComponentsContents() {
        MemoryEditorDialog dialog = editorInside(Optional.of("c5"));
        dialog.model().setCell(1, "7");
        dialog.accept();

        circuit.history().undo();

        assertThat(piece(FIRST).memoryContents()).isEmpty();
    }

    @Test
    void withSinValoresItEditsTheDefinition() {
        MemoryEditorDialog dialog = editorInside(Optional.empty());
        dialog.model().setCell(1, "7");

        dialog.accept();

        assertThat(definitionContents()).isEqualTo("@0 5 7");
        assertThat(piece(FIRST).memoryContents()).isEmpty();
        assertThat(dialog.title()).isEqualTo("Contenido de RAM1 de Memoria chica — todas las piezas · RAM · 256 × 8");
        assertThat(button(dialog.content(), "memory.piece.useForAll")).isNull();
    }

    @Test
    void useForEveryPieceMovesItToTheDefinition() {
        MemoryEditorDialog dialog = editorInside(Optional.of("c5"));
        dialog.model().setCell(1, "7");
        dialog.accept();
        MemoryEditorDialog again = editorInside(Optional.of("c5"));

        button(again.content(), "memory.piece.useForAll").doClick();

        assertThat(definitionContents()).isEqualTo("@0 5 7");
        assertThat(piece(FIRST).memoryContents()).isEmpty();
        assertThat(circuit.history().undoDescription()).contains("command.pieceMemoryForAll");
        circuit.history().undo();
        assertThat(definitionContents()).isEqualTo("@0 5");
        assertThat(piece(FIRST).memoryContents()).containsEntry("c3", "@0 5 7");
    }

    @Test
    void backToTheComponentClearsThePiecesContents() {
        MemoryEditorDialog dialog = editorInside(Optional.of("c5"));
        assertThat(button(dialog.content(), "memory.piece.backToComponent").isEnabled()).isFalse();
        dialog.model().setCell(1, "7");
        dialog.accept();
        MemoryEditorDialog again = editorInside(Optional.of("c5"));
        AbstractButton back = button(again.content(), "memory.piece.backToComponent");
        assertThat(back.isEnabled()).isTrue();

        back.doClick();

        assertThat(piece(FIRST).memoryContents()).isEmpty();
        assertThat(definitionContents()).isEqualTo("@0 5");
    }

    @Test
    void thePieceButtonsSitOnTheLeftOfTheAcceptAndCancelRow() {
        MemoryEditorDialog dialog = editorInside(Optional.of("c5"));
        dialog.content().setSize(900, 500);
        layOut(dialog.content());

        Rectangle accept = boundsIn(dialog.content(), button(dialog.content(), "memory.accept"));
        for (String name : List.of("memory.piece.useForAll", "memory.piece.backToComponent")) {
            Rectangle pieceButton = boundsIn(dialog.content(), button(dialog.content(), name));
            assertThat(pieceButton.getCenterY()).as(name).isEqualTo(accept.getCenterY());
            assertThat(pieceButton.getMaxX()).as(name).isLessThan(accept.getMinX());
        }
    }

    @Test
    void importInsideWritesTheContextPiece() throws IOException {
        Path file = Files.writeString(folder.resolve("programa.hex"), "v2.0 raw\n05 09\n");
        MemoryEditorDialog dialog = editorInside(Optional.of("c9"));

        dialog.importFile(file);
        dialog.accept();

        assertThat(piece(SECOND).memoryContents()).containsEntry("c3", "@0 5 9");
        assertThat(piece(FIRST).memoryContents()).isEmpty();
        assertThat(definitionContents()).isEqualTo("@0 5");
    }

    private MemoryEditorDialog editorInside(Optional<String> context) {
        return new MemoryEditorDialog(body(context), MEMORY, null, NoLinkActions.INSTANCE);
    }

    private OpenDocument body(Optional<String> context) {
        OpenDocument body = circuit.openBody("memoria-chica", context);
        bodies.add(body);
        return body;
    }

    private void piece(ElementId id, int x, String label) {
        circuit.execute(new AddElementsCommand(List.of(new Component(id, SubcircuitTypes.typeIdOf("memoria-chica"),
                new GridPoint(x, 0), Rotation.R0, Map.of(), ""))));
        circuit.execute(new SetLabelCommand(id, label));
    }

    private Component piece(ElementId id) {
        return circuit.document().component(id).orElseThrow();
    }

    private String definitionContents() {
        return (String) circuit.document().definition("memoria-chica").orElseThrow().body().components().getFirst()
                .parameters().get(MemoryTypes.CONTENTS);
    }

    private static Rectangle boundsIn(Container root, java.awt.Component child) {
        return SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), root);
    }

    private static AbstractButton button(Container root, String name) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof AbstractButton found && name.equals(found.getName())) {
                return found;
            }
            if (child instanceof Container container) {
                AbstractButton nested = button(container, name);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }
}
