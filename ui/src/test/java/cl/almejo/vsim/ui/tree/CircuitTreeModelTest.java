package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.search.SearchQuery;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        assertThat(names(instance.children())).containsExactly("a", "Inversor 1", "Inversor 2", "y");
        CircuitTreeNode nested = instance.children().get(2);
        assertThat(nested.path()).isEqualTo("c1/c3");
        assertThat(nested.definitionId()).contains(ExampleDefinitions.INVERTER);
        assertThat(nested.children().get(1).path()).isEqualTo("c1/c3/c2");
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
