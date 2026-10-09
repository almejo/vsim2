package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitFlattenerTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    CircuitFlattenerTest() {
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    private Component add(String typeId, String id, int x, int y) {
        Component component = registry.require(typeId).newComponent(new ElementId(id), new GridPoint(x, y),
                Rotation.R0, Map.of(), "");
        document.add(component);
        return component;
    }

    /** Switch c1 (out at 4,1) → instance c2 of {@code definitionId} at (4,-1) (a at 4,1; y at 8,1) → LED c3 (in at 8,1). */
    private void switchThroughInstanceToLed(String definitionId) {
        add(StandardComponentTypes.SWITCH, "c1", 0, 0);
        add(SubcircuitTypes.typeIdOf(definitionId), "c2", 4, -1);
        add(StandardComponentTypes.LED, "c3", 7, -2);
    }

    private SimulationSnapshot simulate(FlatCircuit flat) {
        SimulationEngine engine = new SimulationEngine();
        engine.update(flat);
        engine.runUntil(20);
        return engine.snapshot();
    }

    @Test
    void anInstanceIsReplacedByItsBodyWithHierarchicalIds() {
        document.putDefinition(ExampleDefinitions.inverter());
        switchThroughInstanceToLed(ExampleDefinitions.INVERTER);
        Netlist netlist = new NetlistCompiler(registry).compile(document);

        FlatCircuit flat = new CircuitFlattener(registry).flatten(document, netlist);

        assertThat(flat.components()).extracting(FlatComponent::id)
                .containsExactly("c1", "c2/c1", "c2/c2", "c2/c3", "c3");
        assertThat(flat.components().get(1).type()).isSameAs(CircuitFlattener.INTERFACE_PROBE);
        String inputNet = netlist.netForPin(new PinRef(new ElementId("c1"), 0)).orElseThrow().id().value();
        FlatNet merged = flat.nets().stream().filter(net -> net.id().equals(inputNet)).findFirst().orElseThrow();
        assertThat(merged.pins()).containsExactlyInAnyOrder(new FlatPinRef("c1", 0), new FlatPinRef("c2/c1", 0),
                new FlatPinRef("c2/c2", 0));
        assertThat(merged.aliases()).singleElement().asString().startsWith("c2/");

        SimulationSnapshot snapshot = simulate(flat);
        assertThat(snapshot.componentState("c3")).isEqualTo(new VisibleState.LedState(Value.ZERO));
        assertThat(snapshot.componentState("c2/c1")).isEqualTo(new VisibleState.LedState(Value.ONE));
        assertThat(snapshot.netValue(merged.aliases().get(0))).isEqualTo(Value.ONE);
    }

    @Test
    void nestedInstancesExpandRecursively() {
        document.putDefinition(ExampleDefinitions.inverter());
        document.putDefinition(ExampleDefinitions.doubleInverter());
        switchThroughInstanceToLed(ExampleDefinitions.DOUBLE_INVERTER);

        FlatCircuit flat = new CircuitFlattener(registry).flatten(document, new NetlistCompiler(registry).compile(document));

        assertThat(flat.components()).extracting(FlatComponent::id).contains("c2/c2/c2", "c2/c3/c2");
        assertThat(simulate(flat).componentState("c3")).isEqualTo(new VisibleState.LedState(Value.ONE));
    }

    @Test
    void aPassThroughTemplateKeepsBothOuterNetsColored() {
        Component input = registry.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 1), Rotation.R0, Map.of(), "a");
        Component output = registry.require(InterfacePins.OUTPUT_PIN).newComponent(new ElementId("c3"),
                new GridPoint(6, 1), Rotation.R0, Map.of(), "y");
        Wire through = new Wire(new ElementId("w2"), new GridPoint(2, 2), new GridPoint(6, 2));
        document.putDefinition(new ComponentDefinition("through", "Puente", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id()),
                        new DefinitionPin(PinSpec.output("y", 4, 2), output.id())),
                new CircuitFragment(List.of(input, output), List.of(through), List.of())));
        switchThroughInstanceToLed("through");
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String switchNet = netlist.netForPin(new PinRef(new ElementId("c1"), 0)).orElseThrow().id().value();
        String ledNet = netlist.netForPin(new PinRef(new ElementId("c3"), 0)).orElseThrow().id().value();
        assertThat(switchNet).isNotEqualTo(ledNet);

        SimulationSnapshot snapshot = simulate(new CircuitFlattener(registry).flatten(document, netlist));

        assertThat(snapshot.netValue(switchNet)).isEqualTo(Value.ONE);
        assertThat(snapshot.netValue(ledNet)).isEqualTo(Value.ONE);
        assertThat(snapshot.componentState("c3")).isEqualTo(new VisibleState.LedState(Value.ONE));
    }

    @Test
    void aShortCircuitInsideATemplateNamesTheNestedDrivers() {
        // Body: input-pin c1 → NOT c2 (in 2,2; out 6,2), and switch c3 (value 1, out 6,2) joined to the NOT output and
        // to output-pin c4. The outer switch drives 1, so the NOT drives 0 against the inner switch's 1.
        Component input = registry.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 1), Rotation.R0, Map.of(), "a");
        Component gate = registry.require(StandardComponentTypes.NOT).newComponent(new ElementId("c2"),
                new GridPoint(2, 0), Rotation.R0, Map.of(), "");
        Component innerSwitch = registry.require(StandardComponentTypes.SWITCH).newComponent(new ElementId("c3"),
                new GridPoint(2, 1), Rotation.R0, Map.of("value", "1"), "");
        Component output = registry.require(InterfacePins.OUTPUT_PIN).newComponent(new ElementId("c4"),
                new GridPoint(6, 1), Rotation.R0, Map.of(), "y");
        document.putDefinition(new ComponentDefinition("shorted", "Cortocircuito", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id()),
                        new DefinitionPin(PinSpec.output("y", 4, 2), output.id())),
                new CircuitFragment(List.of(input, gate, innerSwitch, output), List.of(), List.of())));
        switchThroughInstanceToLed("shorted");

        SimulationSnapshot snapshot = simulate(
                new CircuitFlattener(registry).flatten(document, new NetlistCompiler(registry).compile(document)));

        assertThat(snapshot.shortCircuits()).singleElement()
                .satisfies(shortCircuit -> assertThat(shortCircuit.drivers()).extracting(DriverRef::componentId)
                        .containsExactlyInAnyOrder("c2/c2", "c2/c3"));
    }

    @Test
    void innerInstancesKeepTheirEngineInstancesAcrossAnUnrelatedTopLevelEdit() {
        ComponentType not = registry.require(StandardComponentTypes.NOT);
        AtomicInteger created = new AtomicInteger();
        registry.register(new ComponentType("counted-not", not.category(), not.size(), not.pins(), not.parameters(),
                parameters -> {
                    created.incrementAndGet();
                    return not.behaviorFactory().create(parameters);
                }));
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        List<Component> body = inverter.body().components().stream()
                .map(component -> component.typeId().equals(StandardComponentTypes.NOT)
                        ? new Component(component.id(), "counted-not", component.position(), component.rotation(),
                        component.parameters(), component.label())
                        : component)
                .toList();
        document.putDefinition(new ComponentDefinition("counted", "Contado", "", inverter.size(), inverter.pinout(),
                new CircuitFragment(body, List.of(), List.of())));
        switchThroughInstanceToLed("counted");
        CircuitFlattener flattener = new CircuitFlattener(registry);
        NetlistCompiler compiler = new NetlistCompiler(registry);
        SimulationEngine engine = new SimulationEngine();
        engine.update(flattener.flatten(document, compiler.compile(document)));
        engine.runUntil(20);
        assertThat(created).hasValue(1);

        add(StandardComponentTypes.NOT, "c4", 20, 20);
        engine.update(flattener.flatten(document, compiler.compile(document)));
        engine.runUntil(40);

        assertThat(created).hasValue(1);
        assertThat(engine.snapshot().componentState("c3")).isEqualTo(new VisibleState.LedState(Value.ZERO));
    }

    @Test
    void instancePathsSplitAndJoin() {
        assertThat(InstancePath.child("c5", "c2")).isEqualTo("c5/c2");
        assertThat(InstancePath.topLevel("c5/c2/c1")).isEqualTo("c5");
        assertThat(InstancePath.topLevel("c5")).isEqualTo("c5");
        assertThat(InstancePath.isNested("c5/c2")).isTrue();
        assertThat(InstancePath.isNested("c5")).isFalse();
        assertThat(InstancePath.depth("c5/c2/c1")).isEqualTo(2);
        assertThat(InstancePath.segments("c5/c2")).containsExactly("c5", "c2");
    }

    @Test
    void componentsReachTheEngineWithTheShapeOfTheirParameters() {
        Component splitter = registry.require(StandardComponentTypes.BUS_SPLITTER).newComponent(new ElementId("c1"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 2), "");
        document.add(splitter);
        FlatCircuit flat = new CircuitFlattener(registry).flatten(document,
                new NetlistCompiler(registry).compile(document));
        assertThat(flat.components()).singleElement().satisfies(component -> {
            assertThat(component.type().pinCount()).isEqualTo(3);
            assertThat(component.type().pin(0).width()).isEqualTo(2);
        });
        assertThat(simulate(flat).failure()).isEmpty();
    }

    private Component addWith(String typeId, String id, int x, int y, Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(new ElementId(id), new GridPoint(x, y),
                Rotation.R0, parameters, "");
        document.add(component);
        return component;
    }

    /** 8-bit input-pin c1 (pin 2,1, value 1) → byte-buffer c2 at (2,-1) (a 2,1; y 6,1) → 8-bit output-pin c3 at (6,0). */
    @Test
    void aBusPassesThroughAWideTemplate() {
        document.putDefinition(ExampleDefinitions.byteBuffer());
        addWith(InterfacePins.INPUT_PIN, "c1", 0, 0, Map.of("value", "1", InterfacePins.WIDTH, 8));
        add(SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), "c2", 2, -1);
        addWith(InterfacePins.OUTPUT_PIN, "c3", 6, 0, Map.of(InterfacePins.WIDTH, 8));
        Netlist netlist = new NetlistCompiler(registry).compile(document);

        FlatCircuit flat = new CircuitFlattener(registry).flatten(document, netlist);

        assertThat(flat.components()).filteredOn(component -> component.id().equals("c2/c1")).singleElement()
                .satisfies(probe -> assertThat(probe.type()).isSameAs(CircuitFlattener.interfaceProbe(8)));
        assertThat(flat.nets()).allSatisfy(net -> assertThat(net.widthMismatch()).isFalse());
        SimulationSnapshot snapshot = simulate(flat);
        assertThat(snapshot.componentState("c3")).isEqualTo(new VisibleState.LedState(Value.of(8, 0xFF)));
        assertThat(snapshot.componentState("c2/c2")).isEqualTo(new VisibleState.LedState(Value.of(8, 0xFF)));
        assertThat(CircuitFlattener.interfaceProbe(1)).isSameAs(CircuitFlattener.INTERFACE_PROBE);
        assertThat(CircuitFlattener.interfaceProbe(8).pin(0).width()).isEqualTo(8);
    }

    @Test
    void aOneBitNetOnAWideTemplatePinIsAWidthMismatchAndStaysZ() {
        document.putDefinition(ExampleDefinitions.byteBuffer());
        add(StandardComponentTypes.SWITCH, "c1", -2, 0); // out at (2, 1), on the instance's 8-bit pin a
        add(SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), "c2", 2, -1);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForPin(new PinRef(new ElementId("c1"), 0)).orElseThrow().id().value();

        SimulationSnapshot snapshot = simulate(new CircuitFlattener(registry).flatten(document, netlist));

        assertThat(snapshot.failure()).isEmpty();
        assertThat(snapshot.problems()).filteredOn(WidthMismatch.class::isInstance).extracting(Problem::netId)
                .containsExactly(netId);
        assertThat(snapshot.netValue(netId).isFullyHighImpedance()).isTrue();
    }
}
