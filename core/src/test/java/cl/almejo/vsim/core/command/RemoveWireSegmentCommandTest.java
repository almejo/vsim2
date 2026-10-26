package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoveWireSegmentCommandTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private RemoveWireSegmentCommand removeNear(Wire wire, int x, int y) {
        RemoveWireSegmentCommand command = new RemoveWireSegmentCommand(wire.id(), new GridPoint(x, y), registry);
        command.apply(document);
        return command;
    }

    private Wire current(Wire wire) {
        return document.wire(wire.id()).orElseThrow();
    }

    private void assertSpans(Wire wire, GridPoint start, GridPoint end) {
        assertThat(wire.start()).isEqualTo(start);
        assertThat(wire.end()).isEqualTo(end);
    }

    private Set<DocumentElement> contents() {
        return Set.copyOf(document.elements());
    }

    @Test
    void segmentBetweenTwoJunctionsSplitsTheWire() {
        Wire main = wire(document, 0, 0, 10, 0);
        Wire left = wire(document, 3, 0, 3, 4);
        Wire right = wire(document, 7, 0, 7, 4);
        Netlist before = new NetlistCompiler(registry).compile(document);
        assertThat(before.netForWire(left.id()).map(net -> net.id()))
                .isEqualTo(before.netForWire(right.id()).map(net -> net.id()));

        removeNear(main, 5, 0);

        assertSpans(current(main), new GridPoint(0, 0), new GridPoint(3, 0));
        assertThat(document.wires()).hasSize(4)
                .anySatisfy(piece -> assertSpans(piece, new GridPoint(7, 0), new GridPoint(10, 0)));
        Netlist after = new NetlistCompiler(registry).compile(document);
        assertThat(after.netForWire(left.id()).map(net -> net.id()))
                .isNotEqualTo(after.netForWire(right.id()).map(net -> net.id()));
    }

    @Test
    void undoRestoresExactlyAndRedoReusesTheNewPieceId() {
        Wire main = wire(document, 0, 0, 10, 0);
        wire(document, 3, 0, 3, 4);
        wire(document, 7, 0, 7, 4);
        History history = new History(document);
        Set<DocumentElement> before = contents();
        List<ElementId> orderBefore = document.wires().stream().map(Wire::id).toList();

        history.execute(new RemoveWireSegmentCommand(main.id(), new GridPoint(5, 0), registry));
        Set<DocumentElement> afterFirstApply = contents();
        history.undo();

        assertThat(contents()).isEqualTo(before);
        assertThat(document.wires().stream().map(Wire::id).toList()).isEqualTo(orderBefore);
        history.redo();
        assertThat(contents()).isEqualTo(afterFirstApply);
    }

    @Test
    void stretchAtTheEndShortensTheWireAndKeepsItsId() {
        Wire main = wire(document, 0, 0, 10, 0);
        wire(document, 4, 0, 4, 5);
        removeNear(main, 8, 0);
        assertSpans(current(main), new GridPoint(0, 0), new GridPoint(4, 0));
        assertThat(document.wires()).hasSize(2);
    }

    @Test
    void stretchAtTheStartKeepsTheIdOnTheRemainingPiece() {
        Wire main = wire(document, 0, 0, 10, 0);
        wire(document, 4, 0, 4, 5);
        removeNear(main, 2, 0);
        assertSpans(current(main), new GridPoint(4, 0), new GridPoint(10, 0));
        assertThat(document.wires()).hasSize(2);
    }

    @Test
    void wireWithoutStopsIsRemovedAndUndoKeepsItsPlace() {
        Wire first = wire(document, 0, 5, 3, 5);
        Wire main = wire(document, 0, 0, 10, 0);
        Wire last = wire(document, 0, 9, 3, 9);
        RemoveWireSegmentCommand command = removeNear(main, 5, 0);
        assertThat(document.wire(main.id())).isEmpty();
        command.revert(document);
        assertThat(document.wires()).containsExactly(first, main, last);
    }

    @Test
    void pinsAreStops() {
        document.add(registry.require(StandardComponentTypes.NOT).newComponent(document.newComponentId(),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), ""));
        Wire main = wire(document, -3, 2, 8, 2);
        removeNear(main, 2, 2);
        assertSpans(current(main), new GridPoint(-3, 2), new GridPoint(0, 2));
        assertThat(document.wires()).hasSize(2)
                .anySatisfy(piece -> assertSpans(piece, new GridPoint(4, 2), new GridPoint(8, 2)));
    }

    @Test
    void aCrossingWireWithoutAnEndpointThereIsNotAStop() {
        Wire main = wire(document, 0, 0, 10, 0);
        wire(document, 5, -3, 5, 3);
        removeNear(main, 2, 0);
        assertThat(document.wire(main.id())).isEmpty();
    }

    @Test
    void nearOnAStopRemovesTheStretchAfterIt() {
        Wire main = wire(document, 0, 0, 10, 0);
        wire(document, 5, 0, 5, 4);
        removeNear(main, 5, 0);
        assertSpans(current(main), new GridPoint(0, 0), new GridPoint(5, 0));
    }

    @Test
    void nearOutsideTheWireIsClampedToIt() {
        Wire main = wire(document, 0, 0, 10, 0);
        wire(document, 5, 0, 5, 4);
        RemoveWireSegmentCommand beyondTheEnd = removeNear(main, 20, 3);
        assertSpans(current(main), new GridPoint(0, 0), new GridPoint(5, 0));
        beyondTheEnd.revert(document);
        removeNear(main, -4, 0);
        assertSpans(current(main), new GridPoint(5, 0), new GridPoint(10, 0));
    }

    @Test
    void verticalWiresWorkTheSameWay() {
        Wire main = wire(document, 2, 0, 2, 10);
        wire(document, 2, 6, 8, 6);
        removeNear(main, 2, 8);
        assertSpans(current(main), new GridPoint(2, 0), new GridPoint(2, 6));
    }

    @Test
    void missingWireIsRejected() {
        RemoveWireSegmentCommand command = new RemoveWireSegmentCommand(new ElementId("w99"), GridPoint.ORIGIN, registry);
        assertThatThrownBy(() -> command.apply(document)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptionNamesTheCommand() {
        assertThat(new RemoveWireSegmentCommand(new ElementId("w1"), GridPoint.ORIGIN, registry).description())
                .isEqualTo("command.removeSegment");
    }
}
