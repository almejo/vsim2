package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasteCommandTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument source = new CircuitDocument();
    private final CircuitDocument target = new CircuitDocument();

    /** NOT gate (0,2), wire (4,4)-(8,4) on its output, label "nota" at (0,0). */
    private CircuitFragment fragment() {
        Component gate = registry.require(StandardComponentTypes.BUFFER).newComponent(source.newComponentId(),
                new GridPoint(10, 10), Rotation.R0, Map.of(), "U1");
        Wire wire = new Wire(source.newWireId(), new GridPoint(14, 12), new GridPoint(18, 12));
        TextLabel label = new TextLabel(source.newLabelId(), new GridPoint(10, 8), "nota");
        source.add(gate);
        source.add(wire);
        source.add(label);
        return CircuitFragment.of(source, List.of(gate.id(), wire.id(), label.id()), registry);
    }

    @Test
    void pastesTheFragmentAtTheTargetWithFreshIds() {
        Component resident = registry.require(StandardComponentTypes.BUFFER).newComponent(target.newComponentId(),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        target.add(resident);
        PasteCommand command = new PasteCommand(fragment(), new GridPoint(20, 30));
        command.apply(target);
        assertThat(command.pastedIds()).hasSize(3).doesNotContain(resident.id());
        assertThat(command.pastedIds()).allMatch(id -> target.element(id).isPresent());
        Component pasted = target.components().get(1);
        assertThat(pasted.position()).isEqualTo(new GridPoint(20, 32));
        assertThat(pasted.label()).isEqualTo("U1");
        assertThat(target.wires()).singleElement().satisfies(wire -> {
            assertThat(wire.start()).isEqualTo(new GridPoint(24, 34));
            assertThat(wire.end()).isEqualTo(new GridPoint(28, 34));
        });
        assertThat(target.labels()).singleElement().satisfies(label -> {
            assertThat(label.position()).isEqualTo(new GridPoint(20, 30));
            assertThat(label.text()).isEqualTo("nota");
        });
        assertThat(command.description()).isEqualTo("command.paste");
    }

    @Test
    void undoRemovesEverythingAndRedoReusesTheSameIds() {
        History history = new History(target);
        PasteCommand command = new PasteCommand(fragment(), new GridPoint(5, 5));
        history.execute(command);
        List<ElementId> first = command.pastedIds();
        Set<DocumentElement> pasted = Set.copyOf(target.elements());
        history.undo();
        assertThat(target.isEmpty()).isTrue();
        history.redo();
        assertThat(command.pastedIds()).isEqualTo(first);
        assertThat(Set.copyOf(target.elements())).isEqualTo(pasted);
    }

    @Test
    void pastedWireMergesAndUndoRestoresTheOriginalWire() {
        Wire existing = new Wire(target.newWireId(), new GridPoint(24, 34), new GridPoint(30, 34));
        Wire later = new Wire(target.newWireId(), new GridPoint(0, 50), new GridPoint(0, 54));
        target.add(existing);
        target.add(later);
        History history = new History(target);
        PasteCommand command = new PasteCommand(fragment(), new GridPoint(20, 30));
        history.execute(command);
        assertThat(target.wires()).hasSize(2).anySatisfy(wire -> {
            assertThat(wire.start()).isEqualTo(new GridPoint(24, 34));
            assertThat(wire.end()).isEqualTo(new GridPoint(30, 34));
            assertThat(command.pastedIds()).contains(wire.id());
        });
        assertThat(target.wire(existing.id())).isEmpty();
        history.undo();
        assertThat(target.wires()).containsExactly(existing, later);
        assertThat(target.components()).isEmpty();
    }

    @Test
    void emptyFragmentChangesNothing() {
        PasteCommand command = new PasteCommand(new CircuitFragment(List.of(), List.of(), List.of()), GridPoint.ORIGIN);
        command.apply(target);
        assertThat(target.isEmpty()).isTrue();
        assertThat(command.pastedIds()).isEmpty();
    }

    @Test
    void pastingBringsTheDefinitionAndUndoTakesItAway() {
        CircuitDocument target = new CircuitDocument();
        History history = new History(target);
        Component instance = new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        CircuitFragment fragment = new CircuitFragment(List.of(instance), List.of(), List.of(),
                List.of(ExampleDefinitions.inverter()));

        history.execute(new PasteCommand(fragment, new GridPoint(10, 10)));
        assertThat(target.definitions()).containsExactly(ExampleDefinitions.inverter());
        assertThat(target.components()).hasSize(1);

        history.undo();
        assertThat(target.definitions()).isEmpty();
        assertThat(target.components()).isEmpty();

        history.redo();
        assertThat(target.definitions()).containsExactly(ExampleDefinitions.inverter());
        assertThat(target.components()).hasSize(1);
    }

    @Test
    void pastingKeepsTheTargetsOwnVersion() {
        CircuitDocument target = new CircuitDocument();
        ComponentDefinition own = ExampleDefinitions.inverter().withName("Mío");
        target.putDefinition(own);
        Component instance = new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        PasteCommand paste = new PasteCommand(new CircuitFragment(List.of(instance), List.of(), List.of(),
                List.of(ExampleDefinitions.inverter())), new GridPoint(2, 2));

        paste.apply(target);
        assertThat(target.definition(ExampleDefinitions.INVERTER)).containsSame(own);
        paste.revert(target);
        assertThat(target.definition(ExampleDefinitions.INVERTER)).containsSame(own);
    }

    @Test
    void aPastedDefinitionNestingOneTheDocumentHoldsWithAnotherPinoutStillFlattensAndSimulates() {
        ComponentDefinition original = ExampleDefinitions.inverter();
        ComponentDefinition differentPinout = new ComponentDefinition(original.id(), original.name(), "",
                new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("in", 0, 1), original.pinout().get(0).source()),
                        new DefinitionPin(PinSpec.output("out", 4, 3), original.pinout().get(1).source())),
                original.body());
        CircuitDocument target = new CircuitDocument();
        target.putDefinition(differentPinout);
        Component instance = new Component(new ElementId("c1"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        CircuitFragment fragment = new CircuitFragment(List.of(instance), List.of(), List.of(),
                List.of(ExampleDefinitions.doubleInverter(), original));

        new PasteCommand(fragment, new GridPoint(10, 10)).apply(target);

        assertThat(target.definition(ExampleDefinitions.INVERTER)).contains(differentPinout);
        assertThat(target.definition(ExampleDefinitions.DOUBLE_INVERTER)).contains(ExampleDefinitions.doubleInverter());
        ComponentTypeRegistry targetRegistry = StandardComponentTypes.registry();
        targetRegistry.addResolver(SubcircuitTypes.resolverFor(target));
        ElementId pasted = target.components().get(0).id();
        // The pasted instance sits at (10,10): its input pin is at (10,12) and its output pin at (14,12).
        Component source = targetRegistry.require(StandardComponentTypes.SWITCH).newComponent(target.newComponentId(),
                new GridPoint(6, 11), Rotation.R0, Map.of(), "");
        Component lamp = targetRegistry.require(StandardComponentTypes.LED).newComponent(target.newComponentId(),
                new GridPoint(13, 9), Rotation.R0, Map.of(), "");
        target.add(source);
        target.add(lamp);
        FlatCircuit flat = new CircuitFlattener(targetRegistry).flatten(target,
                new NetlistCompiler(targetRegistry).compile(target));
        assertThat(flat.components()).extracting(FlatComponent::id).contains(
                pasted.value() + "/c2/c2", pasted.value() + "/c3/c2");
        SimulationEngine engine = new SimulationEngine();
        engine.update(flat);
        engine.runUntil(20);
        // The document's inverter was kept with its moved pins, so the pasted body's wiring no longer meets them:
        // the signal does not reach the lamp (it would read ONE with the original pinout).
        assertThat(engine.snapshot().componentState(lamp.id().value()))
                .isEqualTo(new VisibleState.LedState(Value.HIGH_IMPEDANCE));
    }
}
