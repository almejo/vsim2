package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.search.SearchQuery;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The rows of the circuit tree without Swing: every piece in document order, bodies at any depth, flat matches. */
class CircuitTreeModelTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    CircuitTreeModelTest() {
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private Component add(String typeId, int x, int y, String label) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), label);
        document.add(component);
        return component;
    }

    private static List<String> names(List<CircuitTreeNode> nodes) {
        return nodes.stream().map(CircuitTreeNode::name).toList();
    }

    @Test
    void listsEveryPieceInDocumentOrderWithoutWiresOrFrames() {
        add(StandardComponentTypes.BUFFER, 0, 0, "U1");
        add(StandardComponentTypes.LED, 10, 0, "");
        document.add(new Wire(document.newWireId(), new GridPoint(4, 2), new GridPoint(10, 2)));
        document.add(new Frame(document.newFrameId(), new GridPoint(-2, -2), 30, 10, "ALU", FrameTint.NONE, false));
        add(StandardComponentTypes.BUFFER, 20, 0, "");

        CircuitTreeNode root = CircuitTreeModel.build("Sumador", document, registry);

        assertThat(root.isDocument()).isTrue();
        assertThat(root.name()).isEqualTo("Sumador");
        assertThat(names(root.children())).containsExactly("U1", "LED", "Buffer");
        assertThat(root.children().getFirst().kind()).contains("Buffer");
        assertThat(root.children().get(1).kind()).isEmpty();
        assertThat(root.children()).allSatisfy(node -> assertThat(node.definitionId()).isEmpty());
    }

    @Test
    void instancesOfUserComponentsHoldTheirBodiesAtAnyDepth() {
        document.putDefinition(ExampleDefinitions.inverter());
        document.putDefinition(ExampleDefinitions.doubleInverter());
        add(SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 0, 0, "");

        CircuitTreeNode instance = CircuitTreeModel.build("x", document, registry).children().getFirst();

        assertThat(instance.definitionId()).contains(ExampleDefinitions.DOUBLE_INVERTER);
        assertThat(names(instance.children())).containsExactly("a", "Inversor · copia 1", "Inversor · copia 2", "y");
        CircuitTreeNode nested = instance.children().get(2);
        assertThat(nested.path()).isEqualTo("c1/c3");
        assertThat(nested.definitionId()).contains(ExampleDefinitions.INVERTER);
        assertThat(nested.children().get(1).path()).isEqualTo("c1/c3/c2");
    }

    @Test
    void aStateMachineHoldsItsStatesNotItsGeneratedPins() {
        document.putDefinition(MachineDefinitions.create("maquina-abc123", "Máquina 1"));
        add(SubcircuitTypes.typeIdOf("maquina-abc123"), 0, 0, "");

        CircuitTreeNode machine = CircuitTreeModel.build("x", document, registry).children().getFirst();

        assertThat(machine.definitionId()).contains("maquina-abc123");
        assertThat(names(machine.children())).containsExactly("S0");
        CircuitTreeNode state = machine.children().getFirst();
        assertThat(state.path()).isEqualTo("c1#s0");
        assertThat(state.isUserComponent()).isFalse();
        assertThat(state.machineRow().orElseThrow().initial()).isTrue();
        assertThat(state.machineRow().orElseThrow().kind()).isEqualTo(CircuitTreeNode.MachineRow.Kind.STATE);
    }

    private ComponentDefinition machineWith(List<MachineState> states, List<MachineNote> notes) {
        ComponentDefinition created = MachineDefinitions.create("maquina-abc123", "Máquina 1");
        StateMachine base = created.machine().orElseThrow();
        return MachineDefinitions.edited(created, base.withStates(states).withNotes(notes)
                .withInitialStateId(Optional.of("s0")));
    }

    @Test
    void theStatesComeInOrderWithTheirColourThenANotesGroupWithOneRowPerNote() {
        document.putDefinition(machineWith(List.of(
                new MachineState("s0", "Espera", DiagramPoint.ORIGIN, List.of()),
                new MachineState("s1", "Cuenta", DiagramPoint.ORIGIN, List.of(), Optional.of(StateColor.GREEN))),
                List.of(new MachineNote("n1", DiagramPoint.ORIGIN, 160, 120, StickyColor.YELLOW, "Primera\nlínea"),
                        new MachineNote("n2", DiagramPoint.ORIGIN, 160, 120, StickyColor.YELLOW, ""))));
        add(SubcircuitTypes.typeIdOf("maquina-abc123"), 0, 0, "");

        CircuitTreeNode machine = CircuitTreeModel.build("x", document, registry).children().getFirst();

        assertThat(names(machine.children())).containsExactly("Espera", "Cuenta", "Notas (2)");
        assertThat(machine.children().get(0).machineRow().orElseThrow().initial()).isTrue();
        assertThat(machine.children().get(1).machineRow().orElseThrow().initial()).isFalse();
        assertThat(machine.children().get(1).machineRow().orElseThrow().color()).contains(StateColor.GREEN);
        CircuitTreeNode group = machine.children().get(2);
        assertThat(group.machineRow().orElseThrow().kind()).isEqualTo(CircuitTreeNode.MachineRow.Kind.NOTES);
        assertThat(names(group.children())).containsExactly("Primera", "(nota vacía)");
        assertThat(group.children().getFirst().machineRow().orElseThrow().elementId()).isEqualTo("n1");
    }

    @Test
    void noNotesNoGroupAndNoTransitionsAsRows() {
        document.putDefinition(machineWith(List.of(new MachineState("s0", "A", DiagramPoint.ORIGIN, List.of()),
                new MachineState("s1", "B", DiagramPoint.ORIGIN, List.of())), List.of()));
        add(SubcircuitTypes.typeIdOf("maquina-abc123"), 0, 0, "");

        CircuitTreeNode machine = CircuitTreeModel.build("x", document, registry).children().getFirst();

        assertThat(names(machine.children())).containsExactly("A", "B");
    }

    @Test
    void aStateWithAProblemIsMarkedAndAWarningIsNotAnError() {
        // two arrows without a condition out of A: an error on A; B has none
        ComponentDefinition created = MachineDefinitions.create("maquina-abc123", "Máquina 1");
        document.putDefinition(MachineDefinitions.edited(created,
                created.machine().orElseThrow().withStates(List.of(
                        new MachineState("s0", "A", DiagramPoint.ORIGIN, List.of()),
                        new MachineState("s1", "B", DiagramPoint.ORIGIN, List.of())))
                        .withTransitions(List.of(
                                new MachineTransition("t1", "s0", "s1", "", List.of()),
                                new MachineTransition("t2", "s0", "s0", "", List.of())))));
        add(SubcircuitTypes.typeIdOf("maquina-abc123"), 0, 0, "");

        CircuitTreeNode machine = CircuitTreeModel.build("x", document, registry).children().getFirst();

        assertThat(machine.children().get(0).machineRow().orElseThrow().problem())
                .isEqualTo(CircuitTreeNode.MachineRow.Severity.ERROR);
        assertThat(machine.children().get(1).machineRow().orElseThrow().problem())
                .isEqualTo(CircuitTreeNode.MachineRow.Severity.NONE);
    }

    @Test
    void theSearchFindsAStateByNameWithTheMachineInItsRoute() {
        document.putDefinition(machineWith(List.of(new MachineState("s0", "Espera", DiagramPoint.ORIGIN, List.of()),
                new MachineState("s1", "Cuenta", DiagramPoint.ORIGIN, List.of())), List.of()));
        add(SubcircuitTypes.typeIdOf("maquina-abc123"), 0, 0, "");
        CircuitTreeNode root = CircuitTreeModel.build("x", document, registry);

        List<CircuitTreeModel.Match> matches = CircuitTreeModel.search(root, new SearchQuery("cuent", false, false).compile());

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().node().path()).isEqualTo("c1#s1");
        assertThat(matches.getFirst().route()).containsExactly("Máquina 1");
    }

    @Test
    void searchListsTheMatchesFlatWithTheirRoute() {
        document.putDefinition(ExampleDefinitions.inverter());
        document.putDefinition(ExampleDefinitions.doubleInverter());
        add(StandardComponentTypes.BUFFER, 20, 0, "inv");
        add(SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 0, 0, "");
        CircuitTreeNode root = CircuitTreeModel.build("x", document, registry);

        List<CircuitTreeModel.Match> matches = CircuitTreeModel.search(root,
                new SearchQuery("inv", false, false).compile());

        assertThat(matches).extracting(match -> match.node().path())
                .containsExactly("c1", "c2", "c2/c2", "c2/c3");
        assertThat(matches).extracting(CircuitTreeModel.Match::route)
                .containsExactly(List.of(), List.of(), List.of("Doble inversor"), List.of("Doble inversor"));
    }
}
