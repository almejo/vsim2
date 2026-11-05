package cl.almejo.vsim.ui.memory;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
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
import cl.almejo.vsim.core.simulation.MemoryMapFrame;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.app.PlaceKey;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review focus 4 (spec §6.6, §10.7): the same memory id in the circuit and inside a component are two memories, each
 * edited where it is; a memory opened from a component tab shows its context instance, and a ROM's contents are the
 * component's, for every piece.
 */
class MemoryKeysInsideTest {

    /** The memory's id both on the circuit and inside «banco». */
    private static final ElementId MEMORY = new ElementId("c3");

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<OpenDocument> bodies = new ArrayList<>();
    private final List<MemoryMapPanel> panels = new ArrayList<>();

    @AfterEach
    void closeAll() {
        panels.forEach(MemoryMapPanel::dispose);
        bodies.forEach(OpenDocument::close);
        circuit.close();
    }

    @Test
    void sameIdInTwoPlacesOpensTwoEditors() {
        withABankHolding(StandardComponentTypes.ROM, "@0 22");
        place(MEMORY.value(), StandardComponentTypes.ROM, 40, 0, Map.of(MemoryTypes.CONTENTS, "@0 11"));
        OpenDocument body = body(Optional.of("c5"));

        MemoryEditorDialog outside = new MemoryEditorDialog(circuit, MEMORY, null, NoLinkActions.INSTANCE);
        MemoryEditorDialog inside = new MemoryEditorDialog(body, MEMORY, null, NoLinkActions.INSTANCE);

        assertThat(outside.model().valueAt(0)).isEqualTo(0x11);
        assertThat(inside.model().valueAt(0)).isEqualTo(0x22);
        assertThat(PlaceKey.of(circuit, MEMORY)).isNotEqualTo(PlaceKey.of(body, MEMORY));
        assertThat(PlaceKey.of(body, MEMORY)).isEqualTo(PlaceKey.of(body(Optional.of("c5")), MEMORY));
        assertThat(PlaceKey.of(body, MEMORY)).isNotEqualTo(PlaceKey.of(body(Optional.of("c9")), MEMORY));
    }

    @Test
    void ramInsideShowsTheContextInstanceContents() {
        withABankHolding(StandardComponentTypes.RAM, "@0 2A");
        OpenDocument body = body(Optional.of("c9"));
        awaitSimulation(circuit);

        assertThat(body.requestMemoryContents(MEMORY).join()).hasValueSatisfying(contents ->
                assertThat(contents.word(0)).isEqualTo(0x2A));
        body.setContextPath(Optional.empty());
        assertThat(body.requestMemoryContents(MEMORY).join()).isEmpty();
    }

    @Test
    void romTitleSaysAllPieces() {
        withABankHolding(StandardComponentTypes.ROM, "@0 22");
        OpenDocument body = body(Optional.of("c5"));

        MemoryEditorDialog inside = new MemoryEditorDialog(body, MEMORY, null, NoLinkActions.INSTANCE);
        MemoryEditorDialog outside = new MemoryEditorDialog(circuit,
                place("c40", StandardComponentTypes.ROM, 40, 0, Map.of()).id(), null, NoLinkActions.INSTANCE);

        assertThat(inside.title()).isEqualTo("Contenido de ROM1 de Banco — todas las piezas · ROM · 256 × 8");
        assertThat(outside.title()).isEqualTo("Contenido de ROM1 · ROM · 256 × 8");
    }

    @Test
    void theMemoryMapOfAComponentTabFollowsItsContext() {
        withABankHolding(StandardComponentTypes.RAM, "@0 2A");
        OpenDocument body = body(Optional.of("c5"));
        MemoryMapPanel panel = new MemoryMapPanel(body, new MemoryMapActions() { },
                () -> ColorScheme.LIGHT, System::nanoTime, () -> true);
        panels.add(panel);
        panel.follow(Set.of(MEMORY));
        waitUntil(() -> {
            panel.refresh();
            return requestedMemory().equals(Optional.of("c5/c3"));
        });

        body.setContextPath(Optional.of("c9"));
        panel.documentChanged();

        waitUntil(() -> {
            panel.refresh();
            return requestedMemory().equals(Optional.of("c9/c3"));
        });
    }

    private Optional<String> requestedMemory() {
        return circuit.latestMemoryMap().map(MemoryMapFrame::request).map(request -> request.componentId());
    }

    /** «Banco»: a memory c3 of {@code typeId} with {@code contents}; two pieces of it, c5 and c9. */
    private void withABankHolding(String typeId, String contents) {
        Component memory = StandardComponentTypes.registry().require(typeId).newComponent(MEMORY, new GridPoint(0, 0),
                Rotation.R0, Map.of(MemoryTypes.CONTENTS, contents), "");
        circuit.execute(new PutDefinitionsCommand(List.of(new ComponentDefinition("banco", "Banco", "",
                new GridSize(4, 4), List.of(), new CircuitFragment(List.of(memory), List.of(), List.of())))));
        place("c5", SubcircuitTypes.typeIdOf("banco"), 0, 0, Map.of());
        place("c9", SubcircuitTypes.typeIdOf("banco"), 20, 0, Map.of());
    }

    private OpenDocument body(Optional<String> context) {
        OpenDocument body = circuit.openBody("banco", context);
        bodies.add(body);
        return body;
    }

    private Component place(String id, String typeId, int x, int y, Map<String, Object> parameters) {
        Component component = new Component(new ElementId(id), typeId, new GridPoint(x, y), Rotation.R0, parameters,
                "");
        circuit.execute(new AddElementsCommand(List.of(component)));
        return component;
    }
}
