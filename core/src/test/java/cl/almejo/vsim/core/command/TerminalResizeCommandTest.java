package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Changing a Terminal's columns and rows together (Task 7b): one undo step for both parameters, the bottom pins move
 * down or up with the bottom edge and their wires follow, D stays on the left edge. A Normal 80 × 24 Terminal at
 * (10, 10) is 82 × 52 units: CLK at (12, 62) wired to a gate's input at (12, 70), D at (10, 13).
 */
class TerminalResizeCommandTest {

    private static final int DATA = 0;
    private static final int CLOCK = 1;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final Component terminal = place(registry, document, StandardComponentTypes.TERMINAL, 10, 10, "");
    private final Component clockReader = gate(registry, document, 12, 68);

    TerminalResizeCommandTest() {
        wire(document, 12, 62, 12, 70);
    }

    private Component current() {
        return document.component(terminal.id()).orElseThrow();
    }

    private static Map<String, Integer> size(int columns, int rows) {
        return Map.of(TerminalTypes.COLUMNS, columns, TerminalTypes.ROWS, rows);
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
    void bothParametersChangeAndTheBottomPinsCarryTheirWires() {
        History history = new History(document);
        history.execute(ResizeComponentCommand.of(document, registry, terminal.id(), size(40, 12)));

        ComponentType type = registry.typeOf(current());
        assertThat(current().parameters()).containsEntry(TerminalTypes.COLUMNS, 40)
                .containsEntry(TerminalTypes.ROWS, 12);
        assertThat(current().position()).isEqualTo(terminal.position());
        assertThat(type.size()).isEqualTo(new GridSize(42, 28));
        assertThat(type.pinLocation(current(), CLOCK)).isEqualTo(new GridPoint(12, 38));
        assertThat(type.pinLocation(current(), DATA)).isEqualTo(new GridPoint(10, 13));
        assertThat(connected(new PinRef(terminal.id(), CLOCK), new PinRef(clockReader.id(), 0))).isTrue();
        assertThat(history.undoDescription()).contains("command.resizePart");
    }

    @Test
    void oneUndoRestoresBothParametersAndTheWiresExactly() {
        Map<DocumentElement, Long> before = snapshot();
        History history = new History(document);
        history.execute(ResizeComponentCommand.of(document, registry, terminal.id(), size(40, 12)));

        history.undo();

        assertThat(snapshot()).containsExactlyEntriesOf(before);
        assertThat(history.canUndo()).isFalse();
        history.redo();
        assertThat(current().parameters()).containsEntry(TerminalTypes.COLUMNS, 40)
                .containsEntry(TerminalTypes.ROWS, 12);
    }

    @Test
    void onlyTheResizeParametersInTheirRangesAreAccepted() {
        assertThatThrownBy(() -> ResizeComponentCommand.of(document, registry, terminal.id(), size(161, 12)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResizeComponentCommand.of(document, registry, terminal.id(), size(40, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResizeComponentCommand.of(document, registry, terminal.id(),
                Map.of(TerminalTypes.SCROLLBACK, 10))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePreviewPlanOfACornerLeavesTheDocumentAlone() {
        Map<DocumentElement, Long> before = snapshot();
        int[] counter = {0};
        FollowPlan plan = ResizeComponentCommand.wirePlan(document, registry, terminal.id(), size(40, 12),
                () -> new ElementId("preview-" + counter[0]++));

        assertThat(plan.replacedWires()).isNotEmpty();
        assertThat(snapshot()).containsExactlyEntriesOf(before);
    }
}
