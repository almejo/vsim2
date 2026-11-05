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
import cl.almejo.vsim.core.library.KeptOldShape;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.UnsafeDefinitionUpdateException;
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
 * The planning of an inside edit (spec §6.2): never refused for a busy edge or a wire that cannot follow; the
 * instance that cannot take a new pin where it should keeps its old box and pins, and is listed.
 */
class ReplaceDefinitionCommandInsideTest {
    private static final GridPoint BUSY_ORIGIN = new GridPoint(10, 10);
    private static final GridPoint FREE_ORIGIN = new GridPoint(40, 10);

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentDefinition inverter = ExampleDefinitions.inverter();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    ReplaceDefinitionCommandInsideTest() {
        document.putDefinition(inverter);
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    @Test
    void prepareInsideNeverRefusesABusyEdge() {
        Component busy = placeInstance("c20", BUSY_ORIGIN);
        Component free = placeInstance("c21", FREE_ORIGIN);
        ComponentDefinition updated = withExtraInput();
        GridPoint landing = BUSY_ORIGIN.translate(updated.pinout().getLast().spec().position().x(),
                updated.pinout().getLast().spec().position().y());
        Wire wire = new Wire(document.newWireId(), landing.translate(-4, 0), landing);
        document.add(wire);

        ReplaceDefinitionCommand command = prepareInside(updated);
        command.apply(document);

        assertThat(command.keptOldShapes()).containsExactly(new KeptOldShape(busy.id(), inverter.id(), List.of("b")));
        ComponentAppearance kept = document.component(busy.id()).orElseThrow().appearance().orElseThrow();
        assertThat(kept.pins().subList(0, 2)).extracting(PinPlacement::offset)
                .containsExactly(new GridPoint(0, 2), new GridPoint(4, 2));
        assertThat(netOf(new PinRef(busy.id(), 2))).containsExactly(new PinRef(busy.id(), 2));
        assertThat(document.wires()).contains(wire);
        assertThat(document.component(free.id()).orElseThrow().appearance()).isEmpty();
        assertThat(locationOf(new PinRef(free.id(), 2)))
                .isEqualTo(FREE_ORIGIN.translate(0, updated.pinout().getLast().spec().position().y()));
    }

    @Test
    void prepareInsideLeavesUnfollowableWiresLoose() {
        Component instance = placeInstance("c20", BUSY_ORIGIN);
        pinAt(new GridPoint(14, 30));
        Wire wire = new Wire(document.newWireId(), new GridPoint(14, 12), new GridPoint(14, 30));
        document.add(wire);
        pinAt(new GridPoint(15, 12));
        pinAt(new GridPoint(17, 12));
        pinAt(new GridPoint(16, 11));
        pinAt(new GridPoint(16, 13));

        ReplaceDefinitionCommand command = prepareInside(movedOutput(inverter));

        assertThat(command.loosenedPins()).containsExactly(new PinRef(instance.id(), 1));
        command.apply(document);
        assertThat(document.wires()).contains(wire);
    }

    @Test
    void prepareStillRefusesABusyEdge() {
        placeInstance("c20", BUSY_ORIGIN);
        ComponentDefinition updated = withExtraInput();
        GridPoint landing = BUSY_ORIGIN.translate(updated.pinout().getLast().spec().position().x(),
                updated.pinout().getLast().spec().position().y());
        document.add(new Wire(document.newWireId(), landing.translate(-4, 0), landing));

        assertThatThrownBy(() -> ReplaceDefinitionCommand.prepare(document, registry,
                new DefinitionBundle(updated, List.of())))
                .isInstanceOf(UnsafeDefinitionUpdateException.class)
                .satisfies(exception -> assertThat(((UnsafeDefinitionUpdateException) exception).messageKey())
                        .isEqualTo("appearance.update.newPinTouches"));
    }

    @Test
    void anUnusedParentWithAnOwnShapedPieceFollowsInsteadOfRefusing() {
        document.putDefinition(parentWithOwnShapedInverter());
        placeInstance("c20", BUSY_ORIGIN);
        ComponentDefinition updated = withExtraInput();
        assertThatThrownBy(() -> ReplaceDefinitionCommand.prepare(document, registry,
                new DefinitionBundle(updated, List.of())))
                .isInstanceOf(UnsafeDefinitionUpdateException.class);

        ReplaceDefinitionCommand command = prepareInside(updated);
        command.apply(document);

        assertThat(document.definition(inverter.id())).contains(updated);
        Component nested = document.definition("parent").orElseThrow().body().components().getFirst();
        assertThat(nested.appearance().orElseThrow().pins()).hasSize(3);
    }

    @Test
    void aNestedInterfaceChangeOfAPlacedParentIsStillRefusedAsTheBackstopOfInterfaceLocked() {
        // phase 1 (spec §6.2 c): the body place refuses such an edit before it is applied (interfaceLocked), so this
        // backstop is never reached from the editor; Task 15 replaces both with the nested rewrite
        document.putDefinition(ExampleDefinitions.doubleInverter());
        document.add(new Component(new ElementId("c91"), SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER),
                new GridPoint(60, 60), Rotation.R0, Map.of(), ""));

        assertThatThrownBy(() -> prepareInside(movedOutput(inverter)))
                .isInstanceOf(UnsafeDefinitionUpdateException.class)
                .satisfies(exception -> assertThat(((UnsafeDefinitionUpdateException) exception).messageKey())
                        .isEqualTo("appearance.update.nestedGeometry"));
        assertThat(document.definition(inverter.id())).containsSame(inverter);
    }

    private ReplaceDefinitionCommand prepareInside(ComponentDefinition updated) {
        return ReplaceDefinitionCommand.prepareInside(document, registry, new DefinitionBundle(updated, List.of()));
    }

    private Component placeInstance(String id, GridPoint origin) {
        Component placed = new Component(new ElementId(id), SubcircuitTypes.typeIdOf(inverter.id()), origin,
                Rotation.R0, Map.of(), "");
        document.add(placed);
        return placed;
    }

    private void pinAt(GridPoint target) {
        Component pin = registry.require(InterfacePins.INPUT_PIN).newComponent(document.newComponentId(),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        GridPoint offset = registry.typeOf(pin).pinLocation(pin, 0);
        document.add(pin.withPosition(target.translate(-offset.x(), -offset.y())));
    }

    /** The inverter with a second input «b», placed as an inside edit places it (free edge spot). */
    private ComponentDefinition withExtraInput() {
        Component extra = registry.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c9"),
                new GridPoint(0, 8), Rotation.R0, Map.of(), "b");
        List<Component> components = new ArrayList<>(inverter.body().components());
        components.add(extra);
        List<DefinitionPin> pins = new ArrayList<>(inverter.pinout());
        pins.add(new DefinitionPin(PinSpec.input("b", 0, 3), extra.id()));
        ComponentDefinition raw = new ComponentDefinition(inverter.id(), inverter.name(), "", inverter.size(), pins,
                new CircuitFragment(components, inverter.body().wires(), List.of()));
        return AppearanceReconciler.preserveGeneralAppearance(inverter, raw);
    }

    private static ComponentDefinition movedOutput(ComponentDefinition definition) {
        DefinitionPin output = definition.pinout().getLast();
        return new ComponentDefinition(definition.id(), definition.name(), "", new GridSize(6, 4), List.of(
                definition.pinout().getFirst(), new DefinitionPin(PinSpec.output("y", 6, 2), output.source())),
                definition.body());
    }

    /** A definition «parent» holding one inverter with its own 4 x 4 shape (pins a (0,2), y (4,2)). */
    private ComponentDefinition parentWithOwnShapedInverter() {
        ComponentAppearance own = new ComponentAppearance(new GridSize(4, 4), Optional.empty(), List.of(
                new PinPlacement(inverter.pinout().getFirst().source(), new GridPoint(0, 2)),
                new PinPlacement(inverter.pinout().getLast().source(), new GridPoint(4, 2))));
        Component nested = new Component(new ElementId("c2"), SubcircuitTypes.typeIdOf(inverter.id()),
                new GridPoint(2, 0), Rotation.R0, Map.of(), "").withAppearance(Optional.of(own));
        return new ComponentDefinition("parent", "Padre", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(nested), List.of(), List.of()));
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
