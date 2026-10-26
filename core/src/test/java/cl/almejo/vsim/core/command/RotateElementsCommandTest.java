package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static org.assertj.core.api.Assertions.assertThat;

class RotateElementsCommandTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private GridPoint pin(ElementId id, int pinIndex) {
        Component component = document.component(id).orElseThrow();
        return registry.require(component.typeId()).pinLocation(component, pinIndex);
    }

    @Test
    void singleComponentTurnsInPlace() {
        Component not = gate(registry, document, 0, 0);
        new RotateElementsCommand(List.of(not.id()), true, registry).apply(document);
        Component turned = document.component(not.id()).orElseThrow();
        assertThat(turned.rotation()).isEqualTo(Rotation.R90);
        assertThat(turned.position()).isEqualTo(new GridPoint(4, 0));
        assertThat(registry.require(turned.typeId()).bounds(turned)).isEqualTo(new Bounds(0, 0, 4, 4));
        assertThat(pin(not.id(), 0)).isEqualTo(new GridPoint(2, 0));
        assertThat(pin(not.id(), 1)).isEqualTo(new GridPoint(2, 4));
    }

    @Test
    void counterClockwiseTurnsTheOtherWay() {
        Component not = gate(registry, document, 0, 0);
        new RotateElementsCommand(List.of(not.id()), false, registry).apply(document);
        Component turned = document.component(not.id()).orElseThrow();
        assertThat(turned.rotation()).isEqualTo(Rotation.R270);
        assertThat(turned.position()).isEqualTo(new GridPoint(0, 4));
        assertThat(registry.require(turned.typeId()).bounds(turned)).isEqualTo(new Bounds(0, 0, 4, 4));
    }

    @Test
    void groupTurnsAroundItsCenterAndStaysConnected() {
        Component not = gate(registry, document, 0, 0);
        Wire wire = new Wire(document.newWireId(), new GridPoint(4, 2), new GridPoint(8, 2));
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(6, 0), "nota");
        document.add(wire);
        document.add(label);
        new RotateElementsCommand(List.of(not.id(), wire.id(), label.id()), true, registry).apply(document);
        assertThat(document.component(not.id()).orElseThrow().position()).isEqualTo(new GridPoint(6, -2));
        assertThat(document.wire(wire.id()).orElseThrow())
                .isEqualTo(new Wire(wire.id(), new GridPoint(4, 2), new GridPoint(4, 6)));
        assertThat(document.label(label.id()).orElseThrow().position()).isEqualTo(new GridPoint(6, 4));
        assertThat(new NetlistCompiler(registry).compile(document).netForWire(wire.id()).orElseThrow().pins())
                .containsExactly(new PinRef(not.id(), 1));
    }

    @Test
    void groupWithOddBoundsStaysOnTheGridAndRevertsExactly() {
        Component first = gate(registry, document, 0, 0);
        Component second = gate(registry, document, 7, 3);
        Set<DocumentElement> before = Set.copyOf(document.elements());
        RotateElementsCommand command = new RotateElementsCommand(
                List.of(first.id(), second.id(), new ElementId("c99")), false, registry);
        command.apply(document);
        assertThat(Set.copyOf(document.elements())).isNotEqualTo(before);
        // union bounds are (0,0)-(11,7), so the pivot is the grid point nearest its center (5.5, 3.5): (6, 4)
        assertThat(RotateElementsCommand.pivotOf(new Bounds(0, 0, 11, 7))).isEqualTo(new GridPoint(6, 4));
        assertThat(document.component(first.id()).orElseThrow().position()).isEqualTo(new GridPoint(2, 10));
        assertThat(document.component(first.id()).orElseThrow().rotation()).isEqualTo(Rotation.R270);
        assertThat(document.component(second.id()).orElseThrow().position()).isEqualTo(new GridPoint(5, 3));
        assertThat(document.component(second.id()).orElseThrow().rotation()).isEqualTo(Rotation.R270);
        command.revert(document);
        assertThat(Set.copyOf(document.elements())).isEqualTo(before);
        assertThat(command.description()).isEqualTo("command.rotate");
    }

    @Test
    void nothingToRotateChangesNothing() {
        gate(registry, document, 0, 0);
        long revision = document.revision();
        new RotateElementsCommand(List.of(new ElementId("c99")), true, registry).apply(document);
        assertThat(document.revision()).isEqualTo(revision);
    }

    @Test
    void rotatingOnlyATextLabelChangesNothing() {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(3, 3), "nota");
        document.add(label);
        long revision = document.revision();
        RotateElementsCommand command = new RotateElementsCommand(List.of(label.id()), true, registry);
        command.apply(document);
        assertThat(document.revision()).isEqualTo(revision);
        assertThat(document.label(label.id()).orElseThrow()).isEqualTo(label);
        command.revert(document);
        assertThat(document.revision()).isEqualTo(revision);
    }

    @Test
    void reportsWhetherTheTurnWouldChangeAnything() {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(3, 3), "nota");
        document.add(label);
        Component gate = gate(registry, document, 0, 0);
        long revision = document.revision();
        assertThat(new RotateElementsCommand(List.of(label.id()), true, registry).changes(document)).isFalse();
        assertThat(new RotateElementsCommand(List.of(new ElementId("c99")), true, registry).changes(document))
                .isFalse();
        assertThat(new RotateElementsCommand(List.of(gate.id()), true, registry).changes(document)).isTrue();
        assertThat(document.revision()).isEqualTo(revision);
    }
    @Test
    void aFrameTurnsWithTheSelectionAndRevertsExactly() {
        Frame frame = new Frame(document.newFrameId(), new GridPoint(0, 0), 10, 6, "Sumador", FrameTint.BLUE, true);
        document.add(frame);
        Component inside = gate(registry, document, 2, 1);
        RotateElementsCommand command = new RotateElementsCommand(List.of(frame.id(), inside.id()), true, registry);
        command.apply(document);
        // pivot (5, 3): the corners (0, 0) and (10, 6) land on (8, -2) and (2, 8)
        Frame turned = document.frame(frame.id()).orElseThrow();
        assertThat(turned).isEqualTo(frame.withRectangle(new GridPoint(2, -2), 6, 10));
        Bounds gateBounds = registry.require(inside.typeId()).bounds(document.component(inside.id()).orElseThrow());
        assertThat(turned.encloses(gateBounds.x(), gateBounds.y(), gateBounds.maximumX(), gateBounds.maximumY()))
                .isTrue();
        command.revert(document);
        assertThat(document.frame(frame.id()).orElseThrow()).isEqualTo(frame);
    }

    @Test
    void aTurnedFrameKeepsTheMinimumSize() {
        Frame frame = new Frame(document.newFrameId(), new GridPoint(0, 0), Frame.MINIMUM_WIDTH,
                Frame.MINIMUM_HEIGHT, "", FrameTint.NONE, false);
        document.add(frame);
        new RotateElementsCommand(List.of(frame.id()), false, registry).apply(document);
        Frame turned = document.frame(frame.id()).orElseThrow();
        assertThat(turned.width()).isGreaterThanOrEqualTo(Frame.MINIMUM_WIDTH);
        assertThat(turned.height()).isEqualTo(Frame.MINIMUM_WIDTH);
    }
}
