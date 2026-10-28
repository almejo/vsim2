package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.UnsafeDefinitionUpdateException;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Shape edits preserve logic and connectivity and undo as one complete edit. */
class ChangeAppearanceCommandTest {
    private final CircuitDocument document = new CircuitDocument();
    private final ComponentDefinition definition = ExampleDefinitions.inverter();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    ChangeAppearanceCommandTest() {
        document.putDefinition(definition);
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    @Test
    void changingOneInstanceMovesItsConnectedWireAndUndoRestoresEverything() {
        Component instance = instance("c20", 10);
        Component gate = new Component(new ElementId("c21"), StandardComponentTypes.BUFFER, new GridPoint(30, 10),
                Rotation.R0, Map.of(), "");
        document.add(gate);
        Wire wire = new Wire(new ElementId("w30"), new GridPoint(14, 12), new GridPoint(30, 12));
        document.add(wire);
        Connectivity before = Connectivity.of(document.wires(), document, registry);
        ChangeAppearanceCommand command = ChangeAppearanceCommand.forInstance(document, registry, instance.id(),
                Optional.of(wider()));

        command.apply(document);

        assertThat(document.component(instance.id()).orElseThrow().appearance()).contains(wider());
        assertThat(Connectivity.of(document.wires(), document, registry)).isEqualTo(before);
        assertThat(command.plan().disconnected()).isEmpty();
        command.revert(document);
        assertThat(document.component(instance.id())).contains(instance);
        assertThat(document.wires()).containsExactly(wire);
        command.apply(document);
        assertThat(Connectivity.of(document.wires(), document, registry)).isEqualTo(before);
    }

    @Test
    void changingTheDefinitionPreservesInstanceOverridesAndRestoresItsRevisionOnUndo() {
        Component first = instance("c20", 10);
        Component second = instance("c21", 30).withAppearance(Optional.of(wider()));
        document.replace(second);
        ChangeAppearanceCommand command = ChangeAppearanceCommand.forDefinition(document, registry, definition.id(),
                wider());

        command.apply(document);
        assertThat(document.definition(definition.id()).orElseThrow().revision()).isEqualTo(definition.revision() + 1);
        assertThat(registry.typeOf(first).size()).isEqualTo(wider().size());
        assertThat(document.component(second.id()).orElseThrow().appearance()).contains(wider());
        command.revert(document);
        assertThat(document.definition(definition.id())).containsSame(definition);
    }

    @Test
    void aForeignWireContactKeepsTheOldGeometryAndReportsTheAffectedPiece() {
        Component instance = instance("c20", 10);
        document.add(new Wire(new ElementId("w30"), new GridPoint(16, 0), new GridPoint(16, 20)));
        ChangeAppearanceCommand command = ChangeAppearanceCommand.forInstance(document, registry, instance.id(),
                Optional.of(wider()));

        assertThat(command.plan().keptOldShape()).extracting(contact -> contact.instance())
                .containsExactly(instance.id());
        command.apply(document);
        assertThat(registry.typeOf(document.component(instance.id()).orElseThrow()).pin(1).position())
                .isEqualTo(definition.pinout().get(1).spec().position());
    }

    @Test
    void twoRetainedPiecesEachGetTheirOwnLabelAndTheNameOfWhatTheyWouldTouch() {
        Component first = instance("c20", 10).withLabel("U1");
        document.replace(first);
        Component second = instance("c21", 40).withLabel("U2");
        document.replace(second);
        Component probeGate = new Component(new ElementId("c22"), StandardComponentTypes.BUFFER, GridPoint.ORIGIN,
                Rotation.R0, Map.of(StandardComponentTypes.NEGATE, true), "G1");
        GridPoint offset = registry.typeOf(probeGate).pinLocation(probeGate, 0);
        document.add(probeGate.withPosition(new GridPoint(16 - offset.x(), -offset.y())));
        document.add(new Wire(new ElementId("w30"), new GridPoint(16, 0), new GridPoint(16, 20)));
        document.add(new Wire(new ElementId("w33"), new GridPoint(46, 0), new GridPoint(46, 20)));
        Map<ElementId, Component> after = Map.of(first.id(), first.withAppearance(Optional.of(wider())),
                second.id(), second.withAppearance(Optional.of(wider())));

        List<cl.almejo.vsim.core.wiring.ForeignContact> kept = cl.almejo.vsim.core.wiring.ShapeChangePlanner
                .plan(document, registry, registry, after).keptOldShape();

        assertThat(kept).extracting(contact -> contact.instanceName()).containsExactlyInAnyOrder("U1", "U2");
        assertThat(kept).allSatisfy(contact -> {
            assertThat(contact.pinName()).isNotBlank();
            assertThat(contact.touchedNetName()).isNotBlank();
            assertThat(contact.touchedNetName()).doesNotMatch("n\\d+|w\\d+|c\\d+");
        });
        assertThat(kept).extracting(contact -> contact.touchedNetName()).anyMatch(name -> name.contains("G1"));
        assertThat(kept.get(0).touchedNetName()).isNotEqualTo(kept.get(1).touchedNetName());
    }

    @Test
    void aStalePreparedEditCannotOverwriteNewerWiring() {
        Component instance = instance("c20", 10);
        ChangeAppearanceCommand command = ChangeAppearanceCommand.forInstance(document, registry, instance.id(),
                Optional.of(wider()));
        document.add(new Wire(new ElementId("w30"), new GridPoint(0, 0), new GridPoint(0, 1)));

        assertThat(command.isStale(document)).isTrue();
        assertThatThrownBy(() -> command.apply(document)).isInstanceOf(IllegalStateException.class);
        assertThat(document.component(instance.id())).contains(instance);
    }

    @Test
    void nestedGeometryLocksWhileAPlacedPieceUsesItsContainingDefinitionAndUnlocksAfterRemovingIt() {
        document.putDefinition(ExampleDefinitions.doubleInverter());
        ElementId placedParent = new ElementId("c90");
        document.add(new Component(placedParent, SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER),
                new GridPoint(60, 60), Rotation.R0, Map.of(), ""));
        assertThatThrownBy(() -> ChangeAppearanceCommand.forDefinition(document, registry, definition.id(), wider()))
                .isInstanceOf(UnsafeDefinitionUpdateException.class);

        document.remove(placedParent);
        ChangeAppearanceCommand command = ChangeAppearanceCommand.forDefinition(document, registry, definition.id(),
                wider());
        command.apply(document);
        assertThat(document.definition(definition.id()).orElseThrow().size()).isEqualTo(wider().size());
    }

    @Test
    void swappingTheDefinitionsConnectedInputAndOutputKeepsTheirNetsSeparate() {
        Component instance = instance("c20", 10);
        pinAt(new GridPoint(0, 12));
        pinAt(new GridPoint(30, 12));
        document.add(new Wire(document.newWireId(), new GridPoint(0, 12), new GridPoint(10, 12)));
        document.add(new Wire(document.newWireId(), new GridPoint(14, 12), new GridPoint(30, 12)));
        Connectivity before = Connectivity.of(document.wires(), document, registry);
        ComponentAppearance swapped = new ComponentAppearance(definition.size(), Optional.empty(), List.of(
                new PinPlacement(definition.pinout().getFirst().source(), new GridPoint(4, 2)),
                new PinPlacement(definition.pinout().getLast().source(), new GridPoint(0, 2))));
        ChangeAppearanceCommand command = ChangeAppearanceCommand.forDefinition(document, registry, definition.id(),
                swapped);

        command.apply(document);

        assertThat(command.plan().keptOldShape()).isEmpty();
        assertThat(command.plan().disconnected()).isEmpty();
        assertThat(registry.typeOf(instance).pin(0).position()).isEqualTo(new GridPoint(4, 2));
        assertThat(Connectivity.of(document.wires(), document, registry)).isEqualTo(before);
    }

    @Test
    void anUnroutablePinIsListedAsDisconnectedWhileItsOriginalWireStaysUntouched() {
        Component instance = instance("c20", 10);
        pinAt(new GridPoint(14, 30));
        Wire wire = new Wire(document.newWireId(), new GridPoint(14, 12), new GridPoint(14, 30));
        document.add(wire);
        // Every orthogonal arrival at (16,12) would pass another pin, but the destination itself is empty.
        pinAt(new GridPoint(15, 12));
        pinAt(new GridPoint(17, 12));
        pinAt(new GridPoint(16, 11));
        pinAt(new GridPoint(16, 13));
        ChangeAppearanceCommand command = ChangeAppearanceCommand.forInstance(document, registry, instance.id(),
                Optional.of(wider()));

        assertThat(command.plan().disconnected()).containsExactly(
                new PinRef(instance.id(), 1));
        command.apply(document);
        assertThat(document.wires()).containsExactly(wire);
        assertThat(command.plan().keptOldShape()).isEmpty();
    }

    private void pinAt(GridPoint target) {
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(document.newComponentId(),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        GridPoint offset = registry.typeOf(pin).pinLocation(pin, 0);
        document.add(pin.withPosition(target.translate(-offset.x(), -offset.y())));
    }

    private Component instance(String id, int x) {
        Component instance = new Component(new ElementId(id), SubcircuitTypes.typeIdOf(definition.id()),
                new GridPoint(x, 10), Rotation.R0, Map.of(), "");
        document.add(instance);
        return instance;
    }

    private ComponentAppearance wider() {
        return new ComponentAppearance(new GridSize(6, 4), Optional.empty(), List.of(
                new PinPlacement(definition.pinout().get(0).source(), new GridPoint(0, 2)),
                new PinPlacement(definition.pinout().get(1).source(), new GridPoint(6, 2))));
    }
}
