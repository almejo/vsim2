package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
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
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.Trace;
import cl.almejo.vsim.core.waveform.TraceEvent;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationEngineWaveformTest {

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final NetlistCompiler compiler = new NetlistCompiler(registry);
    private final CircuitFlattener flattener = new CircuitFlattener(registry);
    private final CircuitDocument document = new CircuitDocument();
    private final SimulationEngine engine = new SimulationEngine();
    private Netlist netlist = Netlist.EMPTY;

    private ElementId place(String typeId, int x, int y, Map<String, Object> parameters) {
        ElementId id = document.newComponentId();
        document.add(new Component(id, typeId, new GridPoint(x, y), Rotation.R0, parameters, ""));
        return id;
    }

    private void wire(int x1, int y1, int x2, int y2) {
        document.add(new Wire(document.newWireId(), new GridPoint(x1, y1), new GridPoint(x2, y2)));
    }

    private void synchronize() {
        netlist = compiler.compile(document);
        engine.update(flattener.flatten(document, netlist));
    }

    private String netOf(ElementId component, int pin) {
        return netlist.netForPin(new PinRef(component, pin)).orElseThrow().id().value();
    }

    private Trace trace(String channelId) {
        return engine.waveforms().trace(channelId).orElseThrow();
    }

    @Test
    void aChainOfOneUnitInvertersRecordsEachEdgeOneUnitLater() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "0"));   // out (2,1)
        ElementId first = place(TestComponentTypes.INVERTER, 2, 0, Map.of("delay", 1));    // (2,1) → (4,1)
        ElementId second = place(TestComponentTypes.INVERTER, 4, 0, Map.of("delay", 1));   // (4,1) → (6,1)
        synchronize();
        engine.advance(5);
        engine.setProbes(Map.of("a", netOf(source, 0), "b", netOf(first, 1), "c", netOf(second, 1)));
        engine.advance(5);
        engine.interact(source.value());
        engine.advance(5);

        WaveformSnapshot waveforms = engine.waveforms();
        assertThat(waveforms.time()).isEqualTo(15);
        assertThat(trace("a").edgesBetween(6, 15)).containsExactly(TraceEvent.of(10, Value.ONE));
        assertThat(trace("b").edgesBetween(6, 15)).containsExactly(TraceEvent.of(11, Value.ZERO));
        assertThat(trace("c").edgesBetween(6, 15)).containsExactly(TraceEvent.of(12, Value.ONE));
        assertThat(trace("a").valueAt(5)).contains(Value.ZERO);
        assertThat(trace("b").valueAt(5)).contains(Value.ONE);
        assertThat(trace("c").valueAt(5)).contains(Value.ZERO);
        assertThat(trace("a").firstTime()).hasValue(5);
    }

    @Test
    void changesAtTheSameTimeKeepOnlyTheLastValue() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "0"));
        synchronize();
        engine.setProbes(Map.of("a", netOf(source, 0)));
        engine.advance(5);
        engine.interact(source.value());
        engine.interact(source.value());
        engine.advance(1);
        engine.interact(source.value());
        engine.interact(source.value());
        engine.interact(source.value());

        assertThat(trace("a").edgesBetween(0, 6))
                .containsExactly(TraceEvent.of(0, Value.ZERO), TraceEvent.of(6, Value.ONE));
    }

    @Test
    void aChannelKeepsItsHistoryWhenItsNetIsRenamedByAMerge() {
        place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "1"));                // out (2,1)
        ElementId inverter = place(TestComponentTypes.INVERTER, 4, 0, Map.of());     // in (4,1)
        synchronize();
        String inputNet = netOf(inverter, 0);
        engine.setProbes(Map.of("in", inputNet));
        engine.advance(5);

        wire(2, 1, 4, 1);
        synchronize();
        String mergedNet = netOf(inverter, 0);
        assertThat(mergedNet).isNotEqualTo(inputNet);
        engine.setProbes(Map.of("in", mergedNet));
        engine.advance(1);

        assertThat(trace("in").firstTime()).hasValue(0);
        assertThat(trace("in").edgesBetween(0, 6))
                .containsExactly(TraceEvent.of(0, Value.HIGH_IMPEDANCE), TraceEvent.of(5, Value.ONE));
    }

    @Test
    void aDeletedNetLeavesAGapAndTheChannelResumesWhenItComesBack() {
        place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "1"));
        ElementId inverter = place(TestComponentTypes.INVERTER, 2, 0, Map.of());
        synchronize();
        engine.setProbes(Map.of("out", netOf(inverter, 1)));
        engine.advance(5);
        Component removed = document.component(inverter).orElseThrow();
        long order = document.orderOf(inverter);

        document.remove(inverter);
        synchronize();
        engine.advance(5);
        document.restore(removed, order);
        synchronize();
        engine.advance(5);

        assertThat(trace("out").edgesBetween(0, 15)).containsExactly(TraceEvent.of(0, Value.HIGH_IMPEDANCE),
                TraceEvent.of(1, Value.ZERO), TraceEvent.gap(5), TraceEvent.of(10, Value.HIGH_IMPEDANCE),
                TraceEvent.of(11, Value.ZERO));
        assertThat(trace("out").valueAt(7)).isEmpty();
    }

    @Test
    void aChannelOnANetJoinedThroughATemplateFollowsTheRootNet() {
        CircuitDocument templated = new CircuitDocument();
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        standard.addResolver(SubcircuitTypes.resolverFor(templated));
        Component input = standard.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 1), Rotation.R0, Map.of(), "a");
        Component output = standard.require(InterfacePins.OUTPUT_PIN).newComponent(new ElementId("c3"),
                new GridPoint(6, 1), Rotation.R0, Map.of(), "y");
        Wire through = new Wire(new ElementId("w2"), new GridPoint(2, 2), new GridPoint(6, 2));
        templated.putDefinition(new ComponentDefinition("through", "Puente", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id()),
                        new DefinitionPin(PinSpec.output("y", 4, 2), output.id())),
                new CircuitFragment(List.of(input, output), List.of(through), List.of())));
        ElementId switchId = new ElementId("c1");
        ElementId ledId = new ElementId("c3");
        templated.add(standard.require(StandardComponentTypes.SWITCH).newComponent(switchId, new GridPoint(0, 0),
                Rotation.R0, Map.of(), ""));
        templated.add(standard.require(SubcircuitTypes.typeIdOf("through")).newComponent(new ElementId("c2"),
                new GridPoint(4, -1), Rotation.R0, Map.of(), ""));
        templated.add(standard.require(StandardComponentTypes.LED).newComponent(ledId, new GridPoint(7, -2),
                Rotation.R0, Map.of(), ""));
        Netlist templatedNetlist = new NetlistCompiler(standard).compile(templated);
        String ledNet = templatedNetlist.netForPin(new PinRef(ledId, 0)).orElseThrow().id().value();
        FlatCircuit flat = new CircuitFlattener(standard).flatten(templated, templatedNetlist);
        assertThat(flat.nets()).extracting(FlatNet::aliases).anySatisfy(aliases -> assertThat(aliases).contains(ledNet));

        SimulationEngine standardEngine = new SimulationEngine();
        standardEngine.update(flat);
        standardEngine.setProbes(Map.of("led", ledNet));
        standardEngine.advance(5);
        standardEngine.interact(switchId.value());
        standardEngine.advance(5);

        assertThat(standardEngine.waveforms().trace("led").orElseThrow().edgesBetween(0, 10))
                .containsExactly(TraceEvent.of(0, Value.ONE), TraceEvent.of(5, Value.ZERO));
    }

    @Test
    void anOlderSnapshotDoesNotChangeWhenMoreEventsArrive() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "0"));
        synchronize();
        engine.setProbes(Map.of("a", netOf(source, 0)));
        WaveformSnapshot before = engine.waveforms();
        engine.advance(1);
        engine.interact(source.value());
        engine.advance(1);
        WaveformSnapshot after = engine.waveforms();

        assertThat(before.trace("a").orElseThrow().eventCount()).isEqualTo(1);
        assertThat(after.trace("a").orElseThrow().eventCount()).isEqualTo(2);
        assertThat(after.version()).isGreaterThan(before.version());
        assertThat(engine.waveforms().trace("a").orElseThrow()).isSameAs(after.trace("a").orElseThrow());
    }

    @Test
    void droppingAChannelForgetsItAndClearingRestartsFromTheCurrentValue() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "0"));
        synchronize();
        engine.setProbes(Map.of("a", netOf(source, 0), "gone", ""));
        assertThat(engine.hasWaveformChannels()).isTrue();
        assertThat(trace("gone").eventCount()).as("an unbound channel records nothing until it binds").isZero();
        engine.advance(3);
        engine.interact(source.value());
        engine.advance(4);

        engine.clearWaveforms();
        assertThat(trace("a").edgesBetween(0, 7)).containsExactly(TraceEvent.of(7, Value.ONE));

        engine.setProbes(Map.of());
        assertThat(engine.waveforms().traces()).isEmpty();
        assertThat(engine.hasWaveformChannels()).isFalse();
    }
}
