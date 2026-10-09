package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SimulationEngineTest {

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

    private ElementId wire(int x1, int y1, int x2, int y2) {
        ElementId id = document.newWireId();
        document.add(new Wire(id, new GridPoint(x1, y1), new GridPoint(x2, y2)));
        return id;
    }

    private void synchronize() {
        netlist = compiler.compile(document);
        engine.update(flattener.flatten(document, netlist));
    }

    private Value valueAt(ElementId component, int pin) {
        String netId = netlist.netForPin(new PinRef(component, pin)).orElseThrow().id().value();
        return engine.snapshot().netValue(netId);
    }

    @Test
    void sourceDrivesBufferAfterDelay() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "1"));
        ElementId buffer = place(TestComponentTypes.BUFFER, 2, 0, Map.of("delay", 3));
        synchronize();
        engine.advance(0);
        assertThat(valueAt(source, 0)).isEqualTo(Value.ONE);
        assertThat(valueAt(buffer, 1)).isEqualTo(Value.HIGH_IMPEDANCE);
        engine.advance(3);
        assertThat(valueAt(buffer, 1)).isEqualTo(Value.ONE);
        assertThat(engine.now()).isEqualTo(3);
    }

    @Test
    void unconnectedInputGivesHighImpedanceOutput() {
        ElementId buffer = place(TestComponentTypes.BUFFER, 0, 0, Map.of());
        synchronize();
        engine.advance(5);
        assertThat(valueAt(buffer, 1)).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(engine.snapshot().problems()).isEmpty();
    }

    @Test
    void shortCircuitReportsBothDriversAndClearsWhenOneIsRemoved() {
        ElementId low = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "0"));
        ElementId high = place(TestComponentTypes.SOURCE, 4, 0, Map.of("value", "1"));
        wire(2, 1, 6, 1);
        synchronize();
        engine.advance(1);

        assertThat(engine.snapshot().shortCircuits()).singleElement().satisfies(shortCircuit -> {
            assertThat(shortCircuit.sinceTime()).isZero();
            assertThat(shortCircuit.drivers()).extracting(DriverRef::componentId)
                    .containsExactlyInAnyOrder(low.value(), high.value());
        });
        assertThat(valueAt(low, 0).conflict()).isTrue();

        document.remove(high);
        synchronize();
        engine.advance(1);
        assertThat(engine.snapshot().problems()).isEmpty();
        assertThat(valueAt(low, 0)).isEqualTo(Value.ZERO);
    }

    @Test
    void stateIsPreservedAcrossUpdates() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "0"));
        synchronize();
        engine.advance(1);
        engine.interact(source.value());
        engine.advance(1);
        assertThat(valueAt(source, 0)).isEqualTo(Value.ONE);

        place(TestComponentTypes.BUFFER, 20, 20, Map.of());
        synchronize();
        engine.advance(1);
        assertThat(valueAt(source, 0)).isEqualTo(Value.ONE);
        assertThat(engine.snapshot().componentState(source.value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ONE));
    }

    @Test
    void changingParametersRecreatesTheInstance() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "0"));
        synchronize();
        engine.advance(1);
        document.replace(document.component(source).orElseThrow().withParameters(Map.of("value", "1")));
        synchronize();
        engine.advance(1);
        assertThat(valueAt(source, 0)).isEqualTo(Value.ONE);
    }

    @Test
    void removingComponentDropsItsPendingEvents() {
        place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "1"));
        ElementId buffer = place(TestComponentTypes.BUFFER, 2, 0, Map.of("delay", 10));
        ElementId output = wire(4, 1, 8, 1);
        synchronize();
        engine.advance(1);

        document.remove(buffer);
        synchronize();
        engine.advance(20);
        String outputNet = netlist.netForWire(output).orElseThrow().id().value();
        assertThat(engine.snapshot().netValue(outputNet)).isEqualTo(Value.HIGH_IMPEDANCE);
    }

    @Test
    void inverterLoopOscillatesWithoutHanging() {
        ElementId inverter = place(TestComponentTypes.INVERTER, 0, 0, Map.of("delay", 1));
        wire(2, 1, 2, 3);
        wire(0, 3, 2, 3);
        wire(0, 1, 0, 3);
        synchronize();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> engine.advance(100_000));
        Value first = valueAt(inverter, 1);
        engine.advance(1);
        Value second = valueAt(inverter, 1);
        assertThat(first.isFullyDefined()).isTrue();
        assertThat(second).isEqualTo(first.not());
    }

    @Test
    void widthMismatchIsReportedAndReadsAsHighImpedance() {
        ElementId buffer = place(TestComponentTypes.BUFFER, 0, 0, Map.of());
        place(TestComponentTypes.WIDE_PROBE, 2, 1, Map.of());
        synchronize();
        engine.advance(2);
        assertThat(engine.snapshot().problems()).singleElement().isInstanceOf(WidthMismatch.class);
        assertThat(((WidthMismatch) engine.snapshot().problems().getFirst()).pins()).hasSize(2);
        assertThat(valueAt(buffer, 1)).isEqualTo(Value.highImpedance(8));
    }

    @Test
    void interactWithUnknownOrPassiveComponentIsIgnored() {
        ElementId buffer = place(TestComponentTypes.BUFFER, 0, 0, Map.of());
        synchronize();
        engine.interact("does-not-exist");
        engine.interact(buffer.value());
        engine.advance(1);
        assertThat(valueAt(buffer, 1)).isEqualTo(Value.HIGH_IMPEDANCE);
    }

    @Test
    void revisionGrowsWithEveryUpdate() {
        synchronize();
        long first = engine.revision();
        synchronize();
        assertThat(engine.revision()).isEqualTo(first + 1);
        assertThat(engine.snapshot().revision()).isEqualTo(engine.revision());
    }

    @Test
    void explicitDefaultParametersKeepTheInstanceState() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of());
        synchronize();
        engine.interact(source.value());
        engine.advance(1);
        assertThat(valueAt(source, 0)).isEqualTo(Value.ONE);
        document.replace(document.component(source).orElseThrow().withParameters(Map.of("value", "0")));
        synchronize();
        engine.advance(1);
        assertThat(valueAt(source, 0)).isEqualTo(Value.ONE);
    }

    @Test
    void pausedInteractionIsVisibleWithoutAdvancing() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "0"));
        synchronize();
        engine.interact(source.value());
        assertThat(engine.now()).isZero();
        assertThat(valueAt(source, 0)).isEqualTo(Value.ONE);
        assertThat(engine.snapshot().componentState(source.value()))
                .isEqualTo(new VisibleState.SwitchState(Value.ONE));
    }

    @Test
    void flattenCarriesTheDocumentRevisionAndTheSnapshotEchoesIt() {
        assertThat(FlatCircuit.EMPTY.token()).isZero();
        assertThat(SimulationSnapshot.EMPTY.token()).isZero();
        place(TestComponentTypes.SOURCE, 0, 0, Map.of());
        wire(10, 10, 12, 10);
        netlist = compiler.compile(document);
        FlatCircuit circuit = flattener.flatten(document, netlist);
        assertThat(circuit.token()).isEqualTo(document.revision()).isEqualTo(2);
        engine.update(circuit);
        assertThat(engine.snapshot().token()).isEqualTo(2);
        engine.update(new FlatCircuit(7, circuit.components(), circuit.nets()));
        assertThat(engine.snapshot().token()).isEqualTo(7);
    }

    @Test
    void invalidUpdateIsRejectedAndThePreviousCircuitKeepsRunning() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "0"));
        ElementId buffer = place(TestComponentTypes.BUFFER, 2, 0, Map.of("delay", 1));
        synchronize();
        engine.advance(2);
        FlatCircuit good = flattener.flatten(document, netlist);
        FlatComponent sourceComponent = good.components().get(0);
        long revision = engine.revision();

        List<FlatCircuit> invalid = List.of(
                new FlatCircuit(good.components(), List.of(new FlatNet("bad", List.of(new FlatPinRef("missing", 0)), 1, false))),
                new FlatCircuit(good.components(), List.of(new FlatNet("bad", List.of(new FlatPinRef(source.value(), 5)), 1, false))),
                new FlatCircuit(List.of(sourceComponent, sourceComponent), List.of()));
        for (FlatCircuit circuit : invalid) {
            assertThatThrownBy(() -> engine.update(circuit)).isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(engine.revision()).isEqualTo(revision);
        engine.interact(source.value());
        engine.advance(1);
        assertThat(valueAt(buffer, 1)).isEqualTo(Value.ONE);
    }
}
