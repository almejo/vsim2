package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
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
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.AppearanceReconciler;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.UnsafeDefinitionUpdateException;
import cl.almejo.vsim.core.library.UpdateNote;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Actualizar componente» and «Actualizar desde la librería» on placed pieces (spec §6.3): pins matched by source or
 * name keep their wires, new pins land on a free spot, removed pins leave their wires, moved pins take their wires
 * along or keep the old shape, and one undo restores everything.
 */
class ReplaceDefinitionCommandTest {
    private static final GridPoint INSTANCE_ORIGIN = new GridPoint(10, 10);
    private static final GridPoint LEFT_PIN = new GridPoint(0, 12);
    private static final GridPoint RIGHT_PIN = new GridPoint(30, 12);

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentDefinition inverter = ExampleDefinitions.inverter();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private Component instance;
    private Component left;
    private Component right;

    ReplaceDefinitionCommandTest() {
        document.putDefinition(inverter);
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    @Test
    void addingAPinKeepsThePlacedPieceWiredAndPutsTheNewPinOnAFreeEdgeSpot() {
        wiredInstance();
        List<Wire> wiresBefore = document.wires();
        ComponentDefinition updated = AppearanceReconciler.preserveGeneralAppearance(inverter, withExtraInput());

        ReplaceDefinitionCommand command = prepare(updated);
        command.apply(document);

        assertThat(document.wires()).containsExactlyElementsOf(wiresBefore);
        assertThat(netOf(pinNamed("a"))).contains(new PinRef(left.id(), 0));
        assertThat(netOf(pinNamed("y"))).contains(new PinRef(right.id(), 0));
        assertThat(netOf(pinNamed("b"))).containsExactly(pinNamed("b"));
        assertThat(locationOf(pinNamed("b")).x()).isEqualTo(INSTANCE_ORIGIN.x());
        assertThat(command.plan().disconnected()).isEmpty();
        assertThat(command.plan().keptOldShape()).isEmpty();
        assertThat(document.component(instance.id()).orElseThrow().appearance()).isEmpty();
        assertThat(command.notes()).extracting(UpdateNote::messageKey).contains("appearance.update.newPin.left");
    }

    @Test
    void undoRestoresTheDefinitionThePieceAndTheWires() {
        wiredInstance();
        List<Wire> wiresBefore = document.wires();
        ReplaceDefinitionCommand command = prepare(movedOutput(inverter));
        command.apply(document);

        command.revert(document);

        assertThat(document.definition(inverter.id())).containsSame(inverter);
        assertThat(document.component(instance.id())).contains(instance);
        assertThat(document.wires()).containsExactlyInAnyOrderElementsOf(wiresBefore);
    }

    @Test
    void removingAPinLeavesItsWireAndKeepsTheOtherPinsConnected() {
        wiredInstance();
        List<Wire> wiresBefore = document.wires();
        DefinitionPin output = inverter.pinout().getLast();
        ComponentDefinition updated = new ComponentDefinition(inverter.id(), inverter.name(), "", inverter.size(),
                List.of(output), inverter.body());

        ReplaceDefinitionCommand command = prepare(updated);
        command.apply(document);

        assertThat(document.wires()).containsExactlyElementsOf(wiresBefore);
        assertThat(netOf(new PinRef(instance.id(), 0))).contains(new PinRef(right.id(), 0));
        assertThat(netOf(new PinRef(left.id(), 0))).containsExactly(new PinRef(left.id(), 0));
        assertThat(command.notes()).extracting(UpdateNote::messageKey).contains("appearance.update.removedPin");
    }

    @Test
    void aRenamedBodyMatchesItsPinsByNameAndTheWiresFollowTheMovedOutput() {
        wiredInstance();
        ComponentDefinition updated = movedOutput(withNewSourceIds(inverter));

        ReplaceDefinitionCommand command = prepare(updated);
        command.apply(document);

        assertThat(locationOf(new PinRef(instance.id(), 1))).isEqualTo(new GridPoint(16, 12));
        assertThat(netOf(new PinRef(instance.id(), 1))).contains(new PinRef(right.id(), 0));
        assertThat(netOf(new PinRef(instance.id(), 0))).contains(new PinRef(left.id(), 0));
        assertThat(command.plan().disconnected()).isEmpty();
        assertThat(command.plan().keptOldShape()).isEmpty();
    }

    @Test
    void aPieceWithItsOwnShapeKeepsItAndGetsTheNewPinOnAFreeSpot() {
        ComponentAppearance own = new ComponentAppearance(new GridSize(6, 4), Optional.empty(), List.of(
                new PinPlacement(inverter.pinout().getFirst().source(), new GridPoint(0, 2)),
                new PinPlacement(inverter.pinout().getLast().source(), new GridPoint(6, 2))));
        instance = new Component(new ElementId("c20"), SubcircuitTypes.typeIdOf(inverter.id()), INSTANCE_ORIGIN,
                Rotation.R0, Map.of(), "U3").withAppearance(Optional.of(own));
        document.add(instance);
        right = pinAt(RIGHT_PIN);
        document.add(new Wire(document.newWireId(), new GridPoint(16, 12), RIGHT_PIN));

        ReplaceDefinitionCommand command = prepare(
                AppearanceReconciler.preserveGeneralAppearance(inverter, withExtraInput()));
        command.apply(document);

        ComponentAppearance kept = document.component(instance.id()).orElseThrow().appearance().orElseThrow();
        assertThat(kept.size().width()).isEqualTo(6);
        assertThat(netOf(pinNamed("y"))).contains(new PinRef(right.id(), 0));
        assertThat(command.notes()).contains(new UpdateNote("appearance.update.newPinOwnShape", List.of("U3", "b")));
    }

    @Test
    void aMovedPinThatWouldTouchAForeignWireKeepsThePieceInItsOldShape() {
        wiredInstance();
        document.add(new Wire(document.newWireId(), new GridPoint(16, 0), new GridPoint(16, 30)));

        ReplaceDefinitionCommand command = prepare(movedOutput(inverter));
        command.apply(document);

        assertThat(command.plan().keptOldShape()).extracting(contact -> contact.instance())
                .containsExactly(instance.id());
        assertThat(locationOf(new PinRef(instance.id(), 1))).isEqualTo(new GridPoint(14, 12));
        assertThat(document.component(instance.id()).orElseThrow().appearance()).isPresent();
        assertThat(command.notes()).extracting(UpdateNote::messageKey).contains("appearance.update.keptOldShape");
    }

    @Test
    void aPinWhoseWireCannotFollowIsReportedAsLoosened() {
        instance = placeInstance();
        pinAt(new GridPoint(14, 30));
        Wire wire = new Wire(document.newWireId(), new GridPoint(14, 12), new GridPoint(14, 30));
        document.add(wire);
        // Every orthogonal arrival at (16,12) would pass another pin, but the destination itself is empty.
        pinAt(new GridPoint(15, 12));
        pinAt(new GridPoint(17, 12));
        pinAt(new GridPoint(16, 11));
        pinAt(new GridPoint(16, 13));

        ReplaceDefinitionCommand command = prepare(movedOutput(inverter));

        assertThat(command.loosenedPins()).containsExactly(new PinRef(instance.id(), 1));
        command.apply(document);
        assertThat(document.wires()).contains(wire);
    }

    @Test
    void aNewPinThatWouldLandOnAWireRefusesTheUpdate() {
        wiredInstance();
        ComponentDefinition updated = AppearanceReconciler.preserveGeneralAppearance(inverter, withExtraInput());
        GridPoint landing = updated.pinout().getLast().spec().position();
        GridPoint newPin = new GridPoint(INSTANCE_ORIGIN.x() + landing.x(), INSTANCE_ORIGIN.y() + landing.y());
        document.add(new Wire(document.newWireId(), newPin.translate(-4, 0), newPin));

        assertThatThrownBy(() -> prepare(updated))
                .isInstanceOf(UnsafeDefinitionUpdateException.class)
                .satisfies(exception -> assertThat(((UnsafeDefinitionUpdateException) exception).messageKey())
                        .isEqualTo("appearance.update.newPinTouches"));
        assertThat(document.definition(inverter.id())).containsSame(inverter);
    }

    @Test
    void aDefinitionUsedInsideAnotherKeepsItsOldCopyAndSaysWhere() {
        placeInstance();
        document.putDefinition(ExampleDefinitions.doubleInverter());

        assertThatThrownBy(() -> prepare(movedOutput(inverter)))
                .isInstanceOf(UnsafeDefinitionUpdateException.class)
                .satisfies(exception -> assertThat(((UnsafeDefinitionUpdateException) exception).messageKey())
                        .isEqualTo("appearance.update.nestedGeometry"));
        assertThat(document.definition(inverter.id())).containsSame(inverter);
    }

    @Test
    void aStalePreparedUpdateIsNotApplied() {
        placeInstance();
        ReplaceDefinitionCommand command = prepare(movedOutput(inverter));
        document.add(new Wire(document.newWireId(), new GridPoint(0, 0), new GridPoint(0, 1)));

        assertThat(command.isStale(document)).isTrue();
        assertThatThrownBy(() -> command.apply(document)).isInstanceOf(IllegalStateException.class);
        assertThat(document.definition(inverter.id())).containsSame(inverter);
    }

    private ReplaceDefinitionCommand prepare(ComponentDefinition updated) {
        return ReplaceDefinitionCommand.prepare(document, registry, new DefinitionBundle(updated, List.of()));
    }

    /** The inverter at (10,10): pin a at (10,12) wired to an input pin at (0,12), y at (14,12) to one at (30,12). */
    private void wiredInstance() {
        instance = placeInstance();
        left = pinAt(LEFT_PIN);
        right = pinAt(RIGHT_PIN);
        document.add(new Wire(document.newWireId(), LEFT_PIN, new GridPoint(10, 12)));
        document.add(new Wire(document.newWireId(), new GridPoint(14, 12), RIGHT_PIN));
    }

    private Component placeInstance() {
        Component placed = new Component(new ElementId("c20"), SubcircuitTypes.typeIdOf(inverter.id()),
                INSTANCE_ORIGIN, Rotation.R0, Map.of(), "U1");
        document.add(placed);
        return placed;
    }

    private Component pinAt(GridPoint target) {
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(document.newComponentId(),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        GridPoint offset = registry.typeOf(pin).pinLocation(pin, 0);
        Component placed = pin.withPosition(target.translate(-offset.x(), -offset.y()));
        document.add(placed);
        return placed;
    }

    /** The inverter with a second input «b» (as «Actualizar componente» builds it, default spot at (0,3)). */
    private ComponentDefinition withExtraInput() {
        Component extra = registry.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c9"),
                new GridPoint(0, 8), Rotation.R0, Map.of(), "b");
        List<Component> components = new ArrayList<>(inverter.body().components());
        components.add(extra);
        List<DefinitionPin> pins = new ArrayList<>(inverter.pinout());
        pins.add(new DefinitionPin(PinSpec.input("b", 0, 3), extra.id()));
        return new ComponentDefinition(inverter.id(), inverter.name(), "", inverter.size(), pins,
                new CircuitFragment(components, inverter.body().wires(), List.of()));
    }

    /** {@code definition} 6 wide with its output «y» at (6,2), as a library version could bring it. */
    private static ComponentDefinition movedOutput(ComponentDefinition definition) {
        DefinitionPin output = definition.pinout().getLast();
        return new ComponentDefinition(definition.id(), definition.name(), "", new GridSize(6, 4), List.of(
                definition.pinout().getFirst(), new DefinitionPin(PinSpec.output("y", 6, 2), output.source())),
                definition.body());
    }

    /** {@code definition} with new ids for its interface pins (a body rebuilt from a pasted selection). */
    private static ComponentDefinition withNewSourceIds(ComponentDefinition definition) {
        Map<ElementId, ElementId> renamed = Map.of(new ElementId("c1"), new ElementId("c91"),
                new ElementId("c3"), new ElementId("c93"));
        List<Component> components = definition.body().components().stream()
                .map(component -> renamed.containsKey(component.id())
                        ? new Component(renamed.get(component.id()), component.typeId(), component.position(),
                        component.rotation(), component.parameters(), component.label()) : component)
                .toList();
        List<DefinitionPin> pins = definition.pinout().stream()
                .map(pin -> new DefinitionPin(pin.spec(), renamed.get(pin.source()))).toList();
        return new ComponentDefinition(definition.id(), definition.name(), "", definition.size(), pins,
                new CircuitFragment(components, definition.body().wires(), List.of()));
    }

    private PinRef pinNamed(String name) {
        Component current = document.component(instance.id()).orElseThrow();
        List<PinSpec> pins = registry.typeOf(current).pins();
        for (int index = 0; index < pins.size(); index++) {
            if (pins.get(index).name().equals(name)) {
                return new PinRef(instance.id(), index);
            }
        }
        throw new AssertionError("no pin " + name);
    }

    private GridPoint locationOf(PinRef pin) {
        Component current = document.component(pin.componentId()).orElseThrow();
        return registry.typeOf(current).pinLocation(current, pin.pinIndex());
    }

    private Set<PinRef> netOf(PinRef pin) {
        return Connectivity.of(document.wires(), document, registry).pinNets().stream()
                .filter(net -> net.contains(pin)).findFirst().orElse(Set.of(pin));
    }
}
