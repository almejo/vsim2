package cl.almejo.vsim.core.library;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.PinPlacement;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Complete override reconciliation across updated bodies, without introducing collisions or dangling identities. */
class AppearanceReconcilerTest {
    private final ComponentDefinition before = ExampleDefinitions.inverter();

    @Test
    void aChangedSourceIdCanMatchItsUniqueNameDirectionAndWidth() {
        ElementId renamedSource = new ElementId("c99");
        List<Component> components = before.body().components().stream()
                .map(component -> component.id().equals(before.pinout().getFirst().source())
                        ? new Component(renamedSource, component.typeId(), component.position(), component.rotation(),
                        component.parameters(), component.label()) : component).toList();
        ComponentDefinition after = new ComponentDefinition(before.id(), before.name(), before.description(),
                before.size(), List.of(new DefinitionPin(before.pinout().getFirst().spec(), renamedSource),
                before.pinout().getLast()), new CircuitFragment(components, before.body().wires(), List.of()));
        ComponentAppearance override = new ComponentAppearance(new GridSize(6, 6), Optional.empty(), List.of(
                new PinPlacement(before.pinout().getFirst().source(), new GridPoint(0, 1)),
                new PinPlacement(before.pinout().getLast().source(), new GridPoint(6, 5))));

        ComponentAppearance reconciled = AppearanceReconciler.reconcile(before, after, Optional.of(override))
                .kept().orElseThrow();

        assertThat(reconciled.pins().getFirst()).isEqualTo(new PinPlacement(renamedSource, new GridPoint(0, 1)));
        assertThat(reconciled.pins().getLast().offset()).isEqualTo(new GridPoint(6, 5));
    }

    @Test
    void aNewPinGetsAFreeEdgePointWithoutOverwritingAnExistingPlacement() {
        Component extra = new Component(new ElementId("c99"), InterfacePins.INPUT_PIN,
                new GridPoint(0, 8), Rotation.R0, Map.of(), "b");
        List<Component> components = new ArrayList<>(before.body().components());
        components.add(extra);
        List<DefinitionPin> pins = new ArrayList<>(before.pinout());
        pins.add(new DefinitionPin(PinSpec.input("b", 0, 1), extra.id()));
        ComponentDefinition after = new ComponentDefinition(before.id(), before.name(), before.description(),
                before.size(), pins, new CircuitFragment(components, before.body().wires(), List.of()));
        ComponentAppearance override = new ComponentAppearance(new GridSize(4, 4), Optional.empty(), List.of(
                new PinPlacement(before.pinout().getFirst().source(), new GridPoint(0, 1)),
                new PinPlacement(before.pinout().getLast().source(), new GridPoint(4, 2))));

        ComponentAppearance reconciled = AppearanceReconciler.reconcile(before, after, Optional.of(override))
                .kept().orElseThrow();

        assertThat(reconciled.pins()).hasSize(3);
        assertThat(reconciled.pins()).extracting(PinPlacement::offset).doesNotHaveDuplicates();
        assertThat(reconciled.pins().getFirst().offset()).isEqualTo(new GridPoint(0, 1));
    }

    @Test
    void anInvalidOldOverrideIsDroppedAsAWhole() {
        ComponentAppearance invalid = new ComponentAppearance(before.size(), Optional.empty(), List.of(
                new PinPlacement(before.pinout().getFirst().source(), new GridPoint(0, 1)),
                new PinPlacement(before.pinout().getLast().source(), new GridPoint(0, 1))));

        assertThat(AppearanceReconciler.reconcile(before, before, Optional.of(invalid)).kept()).isEmpty();
    }

    @Test
    void aLogicRepairRetainsTheGeneralDrawingAndPinLayoutButUsesTheNewBody() {
        SymbolDrawing drawing = new SymbolDrawing(new GridSize(6, 6), List.of(new SymbolText(new SymbolPoint(8, 8),
                Optional.of("A"), TextSize.MEDIUM, TextAlignment.CENTER)), true);
        ComponentDefinition shaped = before.withGeometry(drawing.size(), List.of(
                new DefinitionPin(PinSpec.input("a", 0, 1), before.pinout().getFirst().source()),
                new DefinitionPin(PinSpec.output("y", 6, 5), before.pinout().getLast().source())), Optional.of(drawing));
        List<Component> body = before.body().components().stream()
                .map(component -> component.typeId().equals(StandardComponentTypes.NOT)
                        ? component.withLabel("repaired") : component)
                .toList();
        ComponentDefinition candidate = new ComponentDefinition(before.id(), "Nuevo nombre", "nuevo", before.size(),
                before.pinout(), new CircuitFragment(body, List.of(), List.of()));

        ComponentDefinition preserved = AppearanceReconciler.preserveGeneralAppearance(shaped, candidate);

        assertThat(preserved.appearance()).contains(drawing);
        assertThat(preserved.pinout()).isEqualTo(shaped.pinout());
        assertThat(preserved.body()).isSameAs(candidate.body());
        assertThat(preserved.name()).isEqualTo(candidate.name());
        assertThat(preserved.revision()).isEqualTo(candidate.revision());
    }
}
