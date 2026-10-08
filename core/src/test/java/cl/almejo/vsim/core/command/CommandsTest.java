package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CommandsTest {

    private CircuitDocument document;
    private Component gate;
    private Wire wire;
    private TextLabel label;

    @BeforeEach
    void createDocument() {
        document = new CircuitDocument();
        gate = new Component(document.newComponentId(), "and2", new GridPoint(2, 2), Rotation.R0, Map.of("delay", 1), "U1");
        wire = new Wire(document.newWireId(), new GridPoint(0, 0), new GridPoint(4, 0));
        label = new TextLabel(document.newLabelId(), new GridPoint(9, 9), "nota");
        document.add(gate);
        document.add(wire);
        document.add(label);
    }

    private Set<DocumentElement> contents() {
        return Set.copyOf(document.elements());
    }

    private void assertApplyAndRevertRestore(Command command) {
        Set<DocumentElement> before = contents();
        command.apply(document);
        assertThat(contents()).isNotEqualTo(before);
        command.revert(document);
        assertThat(contents()).isEqualTo(before);
    }

    @Test
    void addElements() {
        Component other = gate.withPosition(new GridPoint(20, 20));
        Component added = new Component(document.newComponentId(), other.typeId(), other.position(),
                other.rotation(), other.parameters(), "");
        AddElementsCommand command = new AddElementsCommand(List.of(added));
        assertApplyAndRevertRestore(command);
        command.apply(document);
        assertThat(document.component(added.id())).contains(added);
    }

    @Test
    void removeElementsIgnoresMissingIds() {
        RemoveElementsCommand command = new RemoveElementsCommand(List.of(gate.id(), wire.id(), new ElementId("c999")));
        assertApplyAndRevertRestore(command);
        command.apply(document);
        assertThat(document.elements()).containsExactly(label);
    }

    @Test
    void moveElementsTranslatesEveryKind() {
        MoveElementsCommand command = new MoveElementsCommand(List.of(gate.id(), wire.id(), label.id()), 3, -1);
        assertApplyAndRevertRestore(command);
        command.apply(document);
        assertThat(document.component(gate.id()).orElseThrow().position()).isEqualTo(new GridPoint(5, 1));
        assertThat(document.wire(wire.id()).orElseThrow().start()).isEqualTo(new GridPoint(3, -1));
        assertThat(document.label(label.id()).orElseThrow().position()).isEqualTo(new GridPoint(12, 8));
    }

    @Test
    void rotateComponent() {
        assertApplyAndRevertRestore(new RotateComponentCommand(gate.id(), true));
        new RotateComponentCommand(gate.id(), false).apply(document);
        assertThat(document.component(gate.id()).orElseThrow().rotation()).isEqualTo(Rotation.R270);
    }

    @Test
    void changeParameters() {
        assertApplyAndRevertRestore(new ChangeParametersCommand(gate.id(), Map.of("delay", 5)));
    }

    @Test
    void setLabelOnComponentAndTextLabel() {
        assertApplyAndRevertRestore(new SetLabelCommand(gate.id(), "sumador"));
        assertApplyAndRevertRestore(new SetLabelCommand(label.id(), "otra nota"));
    }

    @Test
    void addWireWithoutOverlapJustAdds() {
        Wire touching = new Wire(document.newWireId(), new GridPoint(4, 0), new GridPoint(8, 0));
        AddWireCommand command = new AddWireCommand(touching);
        assertApplyAndRevertRestore(command);
        command.apply(document);
        assertThat(document.wires()).hasSize(2);
    }

    @Test
    void addWireMergesOverlappingCollinearWires() {
        Wire second = new Wire(document.newWireId(), new GridPoint(6, 0), new GridPoint(10, 0));
        document.add(second);
        Wire bridge = new Wire(document.newWireId(), new GridPoint(3, 0), new GridPoint(7, 0));
        AddWireCommand command = new AddWireCommand(bridge);
        assertApplyAndRevertRestore(command);
        command.apply(document);
        assertThat(document.wires()).singleElement().satisfies(merged -> {
            assertThat(merged.id()).isEqualTo(bridge.id());
            assertThat(merged.start()).isEqualTo(new GridPoint(0, 0));
            assertThat(merged.end()).isEqualTo(new GridPoint(10, 0));
        });
    }

    @Test
    void compositeAppliesInOrderAndRevertsInReverse() {
        CompositeCommand command = new CompositeCommand("command.paste", List.of(
                new MoveElementsCommand(List.of(gate.id()), 1, 0),
                new RotateComponentCommand(gate.id(), true)));
        assertApplyAndRevertRestore(command);
        assertThat(command.description()).isEqualTo("command.paste");
    }
}
