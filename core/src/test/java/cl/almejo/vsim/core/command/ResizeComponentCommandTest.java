package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import cl.almejo.vsim.core.wiring.FollowPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.place;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Changing a part's size by its resize parameter (Task 6b): one undo step that sets the parameter and makes the wires
 * of the pins that move follow them. A Teclado at (10, 10): AV at (26, 12) wired to a gate's input at (40, 12), D at
 * (26, 14) wired to another gate's input at (44, 14).
 */
class ResizeComponentCommandTest {

    private static final int AV = 3;
    private static final int D = 4;
    private static final int BOTTOM_PINS = 3;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final Component keyboard = place(registry, document, StandardComponentTypes.KEYBOARD, 10, 10, "");
    private final Component availableReader = gate(registry, document, 40, 10);
    private final Component dataReader = gate(registry, document, 44, 12);

    ResizeComponentCommandTest() {
        wire(document, 26, 12, 40, 12);
        wire(document, 26, 14, 44, 14);
    }

    private Component current() {
        return document.component(keyboard.id()).orElseThrow();
    }

    private Map<DocumentElement, Long> snapshot() {
        Map<DocumentElement, Long> snapshot = new LinkedHashMap<>();
        document.elements().forEach(element -> snapshot.put(element, document.orderOf(element.id())));
        return snapshot;
    }

    private boolean connected(PinRef first, PinRef second) {
        return Connectivity.of(document.wires(), document, registry).pinNets().stream()
                .anyMatch(net -> net.contains(first) && net.contains(second));
    }

    @Test
    void widerTheRightEdgeCarriesAvAndDAndTheirWiresFollow() {
        History history = new History(document);
        history.execute(ResizeComponentCommand.of(document, registry, keyboard.id(), 24));

        ComponentType type = registry.typeOf(current());
        assertThat(current().parameters()).containsEntry(KeyboardTypes.PART_WIDTH, 24);
        assertThat(current().position()).isEqualTo(keyboard.position());
        assertThat(type.size()).isEqualTo(new GridSize(24, 6));
        assertThat(type.pinLocation(current(), AV)).isEqualTo(new GridPoint(34, 12));
        assertThat(type.pinLocation(current(), D)).isEqualTo(new GridPoint(34, 14));
        assertThat(connected(new PinRef(keyboard.id(), AV), new PinRef(availableReader.id(), 0))).isTrue();
        assertThat(connected(new PinRef(keyboard.id(), D), new PinRef(dataReader.id(), 0))).isTrue();
        assertThat(history.undoDescription()).contains("command.resizePart");
    }

    @Test
    void oneUndoRestoresTheWidthAndTheWiresExactly() {
        Map<DocumentElement, Long> before = snapshot();
        History history = new History(document);
        history.execute(ResizeComponentCommand.of(document, registry, keyboard.id(), 30));
        assertThat(snapshot()).isNotEqualTo(before);

        history.undo();

        assertThat(snapshot()).containsExactlyEntriesOf(before);
        assertThat(history.canUndo()).isFalse();
        history.redo();
        assertThat(current().parameters()).containsEntry(KeyboardTypes.PART_WIDTH, 30);
        assertThat(connected(new PinRef(keyboard.id(), AV), new PinRef(availableReader.id(), 0))).isTrue();
    }

    @Test
    void theInputsOnTheBottomEdgeStayWhereTheyWere() {
        ComponentType before = registry.typeOf(keyboard);
        new History(document).execute(ResizeComponentCommand.of(document, registry, keyboard.id(), 40));
        ComponentType after = registry.typeOf(current());
        for (int pin = 0; pin < BOTTOM_PINS; pin++) {
            assertThat(after.pinLocation(current(), pin)).isEqualTo(before.pinLocation(keyboard, pin));
        }
    }

    @Test
    void thePreviewPlanUsesThrowawayIdsAndLeavesTheDocumentAlone() {
        Map<DocumentElement, Long> before = snapshot();
        int[] counter = {0};
        FollowPlan plan = ResizeComponentCommand.wirePlan(document, registry, keyboard.id(), 24,
                () -> new ElementId("preview-" + counter[0]++));

        assertThat(plan.replacedWires()).isNotEmpty();
        assertThat(snapshot()).containsExactlyEntriesOf(before);
    }
}
