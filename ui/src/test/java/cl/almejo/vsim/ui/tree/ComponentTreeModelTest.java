package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentTreeModelTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    ComponentTreeModelTest() {
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    @AfterEach
    void restoreSpanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private Component add(String typeId, int x, int y, String label) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), label);
        document.add(component);
        return component;
    }

    private Frame frame(int x, int y, int width, int height, String title) {
        Frame frame = new Frame(document.newFrameId(), new GridPoint(x, y), width, height, title, FrameTint.NONE,
                false);
        document.add(frame);
        return frame;
    }

    private static List<String> texts(List<ComponentTreeNode> nodes) {
        return nodes.stream().map(ComponentTreeNode::text).toList();
    }

    private static List<String> paths(List<ComponentTreeNode> nodes) {
        return nodes.stream().map(ComponentTreeNode::path).toList();
    }

    @Test
    void listsTheComponentsInDocumentOrderWithLabelAndTypeName() {
        Component first = add(StandardComponentTypes.NOT, 0, 0, "U1");
        Component led = add(StandardComponentTypes.LED, 10, 0, "");
        document.add(new Wire(document.newWireId(), new GridPoint(4, 2), new GridPoint(10, 2)));
        Component last = add(StandardComponentTypes.NOT, 20, 0, "");

        ComponentTreeNode root = ComponentTreeModel.build("Sumador", document, registry, false);

        assertThat(root.kind()).isEqualTo(ComponentTreeNode.Kind.DOCUMENT);
        assertThat(root.path()).isEqualTo(ComponentTreeNode.DOCUMENT_PATH);
        assertThat(root.text()).isEqualTo("Sumador");
        assertThat(texts(root.children())).containsExactly("U1 · NOT", "LED", "NOT");
        assertThat(paths(root.children())).containsExactly(first.id().value(), led.id().value(), last.id().value());
        assertThat(root.children()).allSatisfy(node -> {
            assertThat(node.kind()).isEqualTo(ComponentTreeNode.Kind.COMPONENT);
            assertThat(node.children()).isEmpty();
            assertThat(node.showsValue()).isFalse();
        });
    }

    @Test
    void groupingByTypeSortsTheGroupsByNameAndKeepsDocumentOrderInside() {
        add(StandardComponentTypes.NOT, 0, 0, "");
        add(StandardComponentTypes.LED, 10, 0, "L1");
        add(StandardComponentTypes.NOT, 20, 0, "U2");

        ComponentTreeNode root = ComponentTreeModel.build("Sumador", document, registry, true);

        assertThat(texts(root.children())).containsExactly("LED (1)", "NOT (2)");
        ComponentTreeNode nots = root.children().get(1);
        assertThat(nots.kind()).isEqualTo(ComponentTreeNode.Kind.GROUP);
        assertThat(nots.path()).isEqualTo(ComponentTreeNode.groupPath(StandardComponentTypes.NOT)).isEqualTo("/not");
        assertThat(texts(nots.children())).containsExactly("NOT", "U2 · NOT");
    }

    @Test
    void instancesExpandIntoTheirBodyRecursivelyWithHierarchicalIds() {
        document.putDefinition(ExampleDefinitions.inverter());
        document.putDefinition(ExampleDefinitions.doubleInverter());
        Component instance = add(SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 4, -1, "U1");
        String id = instance.id().value();

        ComponentTreeNode node = ComponentTreeModel.build("Sumador", document, registry, false).children().get(0);

        assertThat(node.text()).isEqualTo("U1 · Doble inversor");
        assertThat(paths(node.children())).containsExactly(InstancePath.child(id, "c1"), InstancePath.child(id, "c2"),
                InstancePath.child(id, "c3"), InstancePath.child(id, "c4"));
        assertThat(texts(node.children()))
                .containsExactly("a · Pin de entrada", "Inversor", "Inversor", "y · Pin de salida");
        ComponentTreeNode inner = node.children().get(1);
        String innerId = InstancePath.child(id, "c2");
        assertThat(paths(inner.children())).containsExactly(InstancePath.child(innerId, "c1"),
                InstancePath.child(innerId, "c2"), InstancePath.child(innerId, "c3"));
        assertThat(texts(inner.children())).containsExactly("a · Pin de entrada", "NOT", "y · Pin de salida");
        assertThat(node.children().get(0).showsValue()).isTrue();
        assertThat(node.children().get(1).showsValue()).isFalse();
        assertThat(inner.children().get(0).showsValue()).isTrue();
        assertThat(inner.children().get(1).showsValue()).isFalse();
    }

    @Test
    void onlyNestedInterfacePinsAndLedsShowTheirValue() {
        document.putDefinition(ExampleDefinitions.inverter());
        Component instance = add(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 4, -1, "");
        Component led = add(StandardComponentTypes.LED, 10, 0, "");
        Component input = add(InterfacePins.INPUT_PIN, 0, 10, "x");
        String id = instance.id().value();
        ComponentTreeNode root = ComponentTreeModel.build("Sumador", document, registry, false);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, 1, 0, true, Map.of(), Map.of(
                InstancePath.child(id, "c1"), new VisibleState.LedState(Value.ONE),
                InstancePath.child(id, "c2"), new VisibleState.LedState(Value.ZERO),
                InstancePath.child(id, "c3"), new VisibleState.LedState(Value.HIGH_IMPEDANCE),
                led.id().value(), new VisibleState.LedState(Value.ONE),
                input.id().value(), new VisibleState.SwitchState(Value.ONE)), List.of(), Optional.empty());

        assertThat(ComponentTreeModel.values(root, snapshot)).containsExactlyInAnyOrderEntriesOf(Map.of(
                InstancePath.child(id, "c1"), "1",
                InstancePath.child(id, "c3"), "Z"));
        assertThat(ComponentTreeModel.values(root, SimulationSnapshot.EMPTY)).isEmpty();
        assertThat(ComponentTreeModel.valueText(Value.ZERO)).isEqualTo("0");
        assertThat(ComponentTreeModel.valueText(Value.ONE.withConflict())).isEqualTo("X");
        assertThat(ComponentTreeModel.valueText(Value.of(8, 0x3F))).isEqualTo("0x3F");
        assertThat(ComponentTreeModel.valueText(Value.highImpedance(4))).isEqualTo("0xZ");
        assertThat(ComponentTreeModel.valueText(Value.of(8, 1).withConflict())).isEqualTo("X");
    }

    @Test
    void anInstanceWithoutItsDefinitionIsALeaf() {
        document.add(new Component(new ElementId("c9"), SubcircuitTypes.typeIdOf("missing"), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), ""));

        assertThat(ComponentTreeModel.build("Sumador", document, registry, false).children()).singleElement()
                .satisfies(node -> {
                    assertThat(node.text()).isEqualTo("subcircuit:missing");
                    assertThat(node.children()).isEmpty();
                });
        assertThat(texts(ComponentTreeModel.build("Sumador", document, registry, true).children()))
                .containsExactly("subcircuit:missing (1)");
    }
    @Test
    void framesComeFirstAndHoldTheComponentsAndFramesDirectlyInside() {
        Component outside = add(StandardComponentTypes.NOT, 60, 0, "");
        Frame outer = frame(0, 0, 50, 40, "ALU");
        Component inOuter = add(StandardComponentTypes.LED, 40, 2, "L1");
        Frame inner = frame(2, 2, 20, 20, "  ");
        Component inInner = add(StandardComponentTypes.NOT, 4, 4, "U1");

        ComponentTreeNode root = ComponentTreeModel.build("Sumador", document, registry, false);

        assertThat(paths(root.children())).containsExactly(ComponentTreeNode.framePath(outer.id()),
                outside.id().value());
        ComponentTreeNode outerNode = root.children().get(0);
        assertThat(outerNode.kind()).isEqualTo(ComponentTreeNode.Kind.FRAME);
        assertThat(outerNode.path()).isEqualTo("frame:" + outer.id().value());
        assertThat(outerNode.text()).isEqualTo("ALU");
        assertThat(paths(outerNode.children())).containsExactly(ComponentTreeNode.framePath(inner.id()),
                inOuter.id().value());
        ComponentTreeNode innerNode = outerNode.children().get(0);
        assertThat(innerNode.text()).isEqualTo("Marco");
        assertThat(paths(innerNode.children())).containsExactly(inInner.id().value());
        assertThat(innerNode.children().get(0).typeId()).isEqualTo(StandardComponentTypes.NOT);
    }

    @Test
    void groupingByTypeAppliesInsideEachFrame() {
        Frame block = frame(0, 0, 50, 40, "ALU");
        add(StandardComponentTypes.NOT, 2, 2, "");
        add(StandardComponentTypes.LED, 10, 2, "");
        add(StandardComponentTypes.NOT, 20, 2, "");
        add(StandardComponentTypes.NOT, 60, 2, "");

        ComponentTreeNode root = ComponentTreeModel.build("Sumador", document, registry, true);

        assertThat(texts(root.children())).containsExactly("ALU", "NOT (1)");
        assertThat(root.children().get(1).path()).isEqualTo("/not");
        ComponentTreeNode blockNode = root.children().get(0);
        assertThat(texts(blockNode.children())).containsExactly("LED (1)", "NOT (2)");
        assertThat(paths(blockNode.children())).containsExactly(
                ComponentTreeNode.groupPath(ComponentTreeNode.framePath(block.id()), StandardComponentTypes.LED),
                ComponentTreeNode.groupPath(ComponentTreeNode.framePath(block.id()), StandardComponentTypes.NOT));
        assertThat(blockNode.children().get(1).typeId()).isEqualTo(StandardComponentTypes.NOT);
    }
}
