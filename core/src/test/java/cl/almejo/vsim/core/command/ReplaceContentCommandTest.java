package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplaceContentCommandTest {

    private CircuitDocument document;
    private Component gate;
    private Wire wire;
    private TextLabel note;

    @BeforeEach
    void createDocument() {
        document = new CircuitDocument();
        gate = new Component(document.newComponentId(), "and", new GridPoint(2, 2), Rotation.R0, Map.of(), "U1");
        wire = new Wire(document.newWireId(), new GridPoint(0, 0), new GridPoint(4, 0));
        note = new TextLabel(document.newLabelId(), new GridPoint(9, 9), "nota");
        document.add(gate);
        document.add(wire);
        document.add(note);
        document.setGuides(List.of(new Guide(Guide.Orientation.VERTICAL, 3)));
    }

    @Test
    void aCopyHasTheSameElementsOrdersDefinitionsProbesGuidesAndNextIds() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        document.putDefinition(inverter);
        document.setProbes(List.of(new Probe(document.newProbeId(), new ProbeTarget.WireTarget(wire.id()), "x")));
        document.setName("Sumador");
        document.remove(note.id());

        CircuitDocument copy = document.copy();

        assertThat(copy.elements()).isEqualTo(document.elements());
        assertThat(copy.orderOf(gate.id())).isEqualTo(document.orderOf(gate.id()));
        assertThat(copy.orderOf(wire.id())).isEqualTo(document.orderOf(wire.id()));
        assertThat(copy.definitions()).containsExactly(inverter);
        assertThat(copy.probes()).isEqualTo(document.probes());
        assertThat(copy.guides()).isEqualTo(document.guides());
        assertThat(copy.name()).isEqualTo("Sumador");
        // The removed note's id is never given again, in the copy as in the document.
        assertThat(copy.newLabelId()).isEqualTo(document.newLabelId());
        assertThat(copy.newProbeId()).isEqualTo(document.newProbeId());
    }

    @Test
    void changingTheCopyNeverChangesTheDocumentNorNotifiesItsListeners() {
        int[] notifications = {0};
        document.addListener((changed, change) -> notifications[0]++);

        CircuitDocument copy = document.copy();
        copy.remove(gate.id());
        copy.add(new TextLabel(copy.newLabelId(), new GridPoint(1, 1), "otra"));

        assertThat(document.elements()).containsExactlyInAnyOrder(gate, wire, note);
        assertThat(notifications[0]).isZero();
    }

    @Test
    void applyingMakesTheDocumentTheAfterStateAndRevertingPutsEverythingBack() {
        CircuitDocument after = document.copy();
        after.remove(wire.id());
        after.replace(gate.withLabel("U2"));
        Component added = new Component(after.newComponentId(), "or", new GridPoint(10, 2), Rotation.R0, Map.of(),
                "");
        after.add(added);
        after.putDefinition(ExampleDefinitions.inverter());
        after.setGuides(List.of());
        Map<ElementId, Long> ordersBefore = orders(document);

        ReplaceContentCommand command = ReplaceContentCommand.between(document, after, "command.aiEdit");
        command.apply(document);

        assertThat(document.elements()).containsExactlyElementsOf(after.elements());
        assertThat(document.orderOf(added.id())).isEqualTo(after.orderOf(added.id()));
        assertThat(document.definitions()).isEqualTo(after.definitions());
        assertThat(document.guides()).isEmpty();
        assertThat(command.addedIds()).containsExactly(added.id());
        assertThat(command.changedIds()).containsExactly(gate.id());
        assertThat(command.removedIds()).containsExactly(wire.id());

        command.revert(document);

        assertThat(document.elements()).containsExactlyInAnyOrder(gate, wire, note);
        assertThat(orders(document)).isEqualTo(ordersBefore);
        assertThat(document.definitions()).isEmpty();
        assertThat(document.guides()).containsExactly(new Guide(Guide.Orientation.VERTICAL, 3));
    }

    @Test
    void anElementTakenOutAndPutBackAtANewOrderIsMovedToThatOrder() {
        CircuitDocument after = document.copy();
        after.remove(gate.id());
        after.add(gate);

        ReplaceContentCommand command = ReplaceContentCommand.between(document, after, "command.aiEdit");
        command.apply(document);

        assertThat(document.orderOf(gate.id())).isEqualTo(after.orderOf(gate.id()));
        command.revert(document);
        assertThat(document.orderOf(gate.id())).isZero();
    }

    @Test
    void theWholeChangeNotifiesTheListenersOnce() {
        CircuitDocument after = document.copy();
        after.remove(wire.id());
        after.remove(note.id());
        after.replace(gate.withLabel("U2"));
        int[] notifications = {0};
        document.addListener((changed, change) -> notifications[0]++);

        ReplaceContentCommand.between(document, after, "command.aiEdit").apply(document);

        assertThat(notifications[0]).isOne();
    }

    @Test
    void aDocumentThatNoLongerMatchesTheBeforeStateIsRefusedUntouched() {
        CircuitDocument after = document.copy();
        after.remove(wire.id());
        after.replace(gate.withLabel("U2"));
        ReplaceContentCommand command = ReplaceContentCommand.between(document, after, "command.aiEdit");
        document.replace(gate.withPosition(new GridPoint(30, 30)));
        List<DocumentElement> before = document.elements();

        assertThatThrownBy(() -> command.apply(document)).isInstanceOf(IllegalStateException.class);
        assertThat(document.elements()).isEqualTo(before);
    }

    @Test
    void equalStatesChangeNothing() {
        ReplaceContentCommand command = ReplaceContentCommand.between(document, document.copy(), "command.aiEdit");

        assertThat(command.changesNothing()).isTrue();
        assertThat(command.description()).isEqualTo("command.aiEdit");
    }

    @Test
    void probesFollowTheAfterState() {
        CircuitDocument after = document.copy();
        after.setProbes(List.of(new Probe(after.newProbeId(), new ProbeTarget.WireTarget(wire.id()), "x")));

        ReplaceContentCommand command = ReplaceContentCommand.between(document, after, "command.aiEdit");
        command.apply(document);
        assertThat(document.probes()).isEqualTo(after.probes());
        command.revert(document);
        assertThat(document.probes()).isEmpty();
    }

    @Test
    void rebasedOnAClickedSwitchKeepsTheClickUnlessTheChangeSetsTheValueItself() {
        Component toggle = new Component(document.newComponentId(), StandardComponentTypes.SWITCH, new GridPoint(30, 0),
                Rotation.R0, Map.of(StandardComponentTypes.VALUE, "1"), null);
        document.add(toggle);
        CircuitDocument labelled = document.copy();
        labelled.replace(toggle.withLabel("A"));
        CircuitDocument valued = document.copy();
        valued.replace(toggle.withParameters(Map.of(StandardComponentTypes.VALUE, "Z")));
        ReplaceContentCommand label = ReplaceContentCommand.between(document, labelled, "command.aiEdit");
        ReplaceContentCommand value = ReplaceContentCommand.between(document, valued, "command.aiEdit");
        assertThat(label.rebasedOnSwitchValuesOf(document)).as("nothing clicked: the same command").isSameAs(label);
        document.replace(toggle.withParameters(Map.of(StandardComponentTypes.VALUE, "0")));

        ReplaceContentCommand rebasedLabel = label.rebasedOnSwitchValuesOf(document);
        rebasedLabel.apply(document);
        assertThat(document.component(toggle.id()).orElseThrow().parameters())
                .containsEntry(StandardComponentTypes.VALUE, "0");
        assertThat(document.component(toggle.id()).orElseThrow().label()).isEqualTo("A");
        rebasedLabel.revert(document);

        value.rebasedOnSwitchValuesOf(document).apply(document);
        assertThat(document.component(toggle.id()).orElseThrow().parameters())
                .containsEntry(StandardComponentTypes.VALUE, "Z");
    }

    private static Map<ElementId, Long> orders(CircuitDocument document) {
        Map<ElementId, Long> orders = new LinkedHashMap<>();
        document.elements().forEach(element -> orders.put(element.id(), document.orderOf(element.id())));
        return orders;
    }
}
