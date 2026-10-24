package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.command.ReplaceDefinitionCommand;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Updating a saved child must not detach its parent's wires from the child's interface. */
class DefinitionUpdateSafetyTest {
    @Test
    void replacingANestedChildWithMovedPinsKeepsTheOldDefinition() {
        CircuitDocument document = new CircuitDocument();
        ComponentDefinition original = ExampleDefinitions.inverter();
        document.putDefinition(original);
        document.putDefinition(ExampleDefinitions.doubleInverter());
        ComponentDefinition changed = new ComponentDefinition(original.id(), original.name(), original.description(),
                new GridSize(6, 4), List.of(original.pinout().get(0),
                new DefinitionPin(PinSpec.output("y", 6, 2), original.pinout().get(1).source())), original.body());

        assertThatThrownBy(() -> new ReplaceDefinitionCommand(new DefinitionBundle(changed, List.of())).apply(document))
                .isInstanceOf(UnsafeDefinitionUpdateException.class);

        assertThat(document.definition(original.id())).containsSame(original);
    }

    @Test
    void renamingANestedChildKeepsItsConnectionsAndCanBeUndone() {
        CircuitDocument document = new CircuitDocument();
        ComponentDefinition original = ExampleDefinitions.inverter();
        document.putDefinition(original);
        document.putDefinition(ExampleDefinitions.doubleInverter());
        ReplaceDefinitionCommand command = new ReplaceDefinitionCommand(
                new DefinitionBundle(original.withName("Nuevo nombre"), List.of()));

        command.apply(document);
        assertThat(document.definition(original.id()).orElseThrow().name()).isEqualTo("Nuevo nombre");
        command.revert(document);
        assertThat(document.definition(original.id())).containsSame(original);
    }

    @Test
    void anUnsupportedGeometryUpdateOfALiveTopLevelPieceKeepsItsOldCopy() {
        CircuitDocument document = new CircuitDocument();
        ComponentDefinition original = ExampleDefinitions.inverter();
        document.putDefinition(original);
        document.add(new Component(new ElementId("c20"), SubcircuitTypes.typeIdOf(original.id()), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), ""));
        ComponentDefinition changed = new ComponentDefinition(original.id(), original.name(), "", new GridSize(6, 4),
                List.of(original.pinout().getFirst(), new DefinitionPin(PinSpec.output("y", 6, 2),
                original.pinout().getLast().source())), original.body());

        assertThatThrownBy(() -> new ReplaceDefinitionCommand(new DefinitionBundle(changed, List.of())).apply(document))
                .isInstanceOf(UnsafeDefinitionUpdateException.class)
                .satisfies(exception -> assertThat(((UnsafeDefinitionUpdateException) exception).messageKey())
                        .isEqualTo("appearance.update.unsafeGeometry"));
        assertThat(document.definition(original.id())).containsSame(original);
    }

    @Test
    void sourceOnlyUpdatesReconcileTopLevelAndNestedOverridesAndUndoRestoresBoth() {
        CircuitDocument document = new CircuitDocument();
        ComponentDefinition original = ExampleDefinitions.inverter();
        document.putDefinition(original);
        ComponentAppearance appearance = new ComponentAppearance(new GridSize(6, 6), Optional.empty(), List.of(
                new PinPlacement(original.pinout().getFirst().source(), new GridPoint(0, 1)),
                new PinPlacement(original.pinout().getLast().source(), new GridPoint(6, 5))));
        Component instance = new Component(new ElementId("c20"), SubcircuitTypes.typeIdOf(original.id()),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "").withAppearance(Optional.of(appearance));
        document.add(instance);
        ComponentDefinition parent = ExampleDefinitions.doubleInverter();
        List<Component> children = parent.body().components().stream()
                .map(component -> component.typeId().equals(instance.typeId())
                        ? component.withAppearance(Optional.of(appearance)) : component).toList();
        ComponentDefinition containing = new ComponentDefinition(parent.id(), parent.name(), parent.description(),
                parent.size(), parent.pinout(), new CircuitFragment(children, parent.body().wires(), List.of()));
        document.putDefinition(containing);
        ElementId newSource = new ElementId("c99");
        List<Component> components = original.body().components().stream()
                .map(component -> component.id().equals(original.pinout().getFirst().source())
                        ? new Component(newSource, component.typeId(), component.position(), component.rotation(),
                        component.parameters(), component.label()) : component).toList();
        ComponentDefinition changed = new ComponentDefinition(original.id(), original.name(), original.description(),
                original.size(), List.of(new DefinitionPin(original.pinout().getFirst().spec(), newSource),
                original.pinout().getLast()), new CircuitFragment(components, original.body().wires(), List.of()));
        ReplaceDefinitionCommand command = new ReplaceDefinitionCommand(new DefinitionBundle(changed, List.of()));

        command.apply(document);
        ComponentAppearance updated = document.component(instance.id()).orElseThrow().appearance().orElseThrow();
        assertThat(updated.pins().getFirst()).isEqualTo(new PinPlacement(newSource, new GridPoint(0, 1)));
        assertThat(document.definition(parent.id()).orElseThrow().body().components().stream()
                .filter(component -> component.typeId().equals(instance.typeId())))
                .allSatisfy(component -> assertThat(component.appearance().orElseThrow().pins().getFirst().source())
                        .isEqualTo(newSource));
        command.revert(document);
        assertThat(document.component(instance.id())).contains(instance);
        assertThat(document.definition(parent.id())).containsSame(containing);
        assertThat(document.definition(original.id())).containsSame(original);
    }
}
