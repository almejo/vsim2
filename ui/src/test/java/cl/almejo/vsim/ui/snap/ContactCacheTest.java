package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The contacts of a drag are worked out once per snapped delta and only look at the moved box (spec §3.7, cost). */
class ContactCacheTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final ComponentType not = openDocument.registry().require(StandardComponentTypes.BUFFER);
    private final ContactCache cache = new ContactCache();

    @AfterEach
    void close() {
        openDocument.close();
    }

    private Component gate(int x, int y) {
        return not.newComponent(openDocument.document().newComponentId(), new GridPoint(x, y), Rotation.R0, Map.of(),
                "");
    }

    private List<NewContact> contacts(List<? extends DocumentElement> moved, int deltaX, int deltaY) {
        CircuitDocument document = openDocument.document();
        Set<ElementId> ids = moved.stream().map(DocumentElement::id)
                .collect(Collectors.toSet());
        return cache.contacts(document, openDocument.registry(), moved, deltaX, deltaY, ids);
    }

    @Test
    void theSameDeltaIsWorkedOutOnceUntilTheDocumentChangesOrTheDragEnds() {
        Component gate = gate(10, 10);
        openDocument.execute(new AddElementsCommand(List.of(gate,
                new Wire(openDocument.document().newWireId(), new GridPoint(10, 15), new GridPoint(10, 25)))));

        assertThat(contacts(List.of(gate), 0, 8)).hasSize(1);
        assertThat(contacts(List.of(gate), 0, 8)).hasSize(1);
        assertThat(cache.probeRuns()).isEqualTo(1);
        assertThat(contacts(List.of(gate), 0, 30)).isEmpty();
        assertThat(cache.probeRuns()).isEqualTo(2);
        assertThat(contacts(List.of(gate), 0, 8)).hasSize(1);
        assertThat(cache.probeRuns()).isEqualTo(2);

        openDocument.execute(new AddElementsCommand(List.of(gate(40, 40))));
        assertThat(contacts(List.of(gate), 0, 8)).hasSize(1);
        assertThat(cache.probeRuns()).isEqualTo(3);

        cache.clear();
        assertThat(contacts(List.of(gate), 0, 8)).hasSize(1);
        assertThat(cache.probeRuns()).isEqualTo(4);
    }

    @Test
    void aFortyPartMoveOnATwoThousandElementCircuitOnlyLooksAtItsBox() {
        List<DocumentElement> circuit = new ArrayList<>();
        for (int index = 0; index < 1000; index++) {
            int x = (index % 40) * 10;
            int y = (index / 40) * 10;
            circuit.add(not.newComponent(new ElementId("g" + index), new GridPoint(x, y), Rotation.R0, Map.of(), ""));
            circuit.add(new Wire(new ElementId("w" + index), new GridPoint(x + 4, y + 2),
                    new GridPoint(x + 6, y + 2)));
        }
        openDocument.execute(new AddElementsCommand(circuit));
        List<Component> moved = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            moved.add(openDocument.document().component(circuit.get(2 * index).id()).orElseThrow());
        }

        contacts(moved, 0, 300);
        // the first row of 40 gates moved far below the circuit: nothing there to look at
        assertThat(cache.lastVisitedCount()).isZero();
        contacts(moved, 0, 10); // onto the second row
        assertThat(cache.lastVisitedCount()).isPositive().isLessThan(200);
    }
}
