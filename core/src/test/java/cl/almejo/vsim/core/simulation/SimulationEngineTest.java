package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
    void aNetValueIsReadWithoutASnapshotAndAnUnknownNetIsHighImpedance() {
        ElementId source = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "1"));
        ElementId buffer = place(TestComponentTypes.BUFFER, 2, 0, Map.of("delay", 3));
        synchronize();
        engine.advance(3);
        String netId = netlist.netForPin(new PinRef(buffer, 1)).orElseThrow().id().value();

        assertThat(engine.netValue(netId)).isEqualTo(Value.ONE).isEqualTo(engine.snapshot().netValue(netId));
        assertThat(engine.netValue(netlist.netForPin(new PinRef(source, 0)).orElseThrow().id().value()))
                .isEqualTo(Value.ONE);
        assertThat(engine.netValue("no-such-net")).isEqualTo(Value.HIGH_IMPEDANCE);
    }

    @Test
    void nextEventTimeIsThePendingGateOutputAndEmptyOnceStable() {
        assertThat(engine.nextEventTime()).isEmpty();
        place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "1"));
        place(TestComponentTypes.BUFFER, 2, 0, Map.of("delay", 3));
        synchronize();
        assertThat(engine.nextEventTime()).hasValue(3);
        engine.runUntil(3);
        assertThat(engine.nextEventTime()).isEmpty();
        assertThat(engine.now()).isEqualTo(3);
    }

    @Test
    void unconnectedInputGivesHighImpedanceOutput() {
        ElementId buffer = place(TestComponentTypes.BUFFER, 0, 0, Map.of());
        synchronize();
        engine.advance(5);
        assertThat(valueAt(buffer, 1)).isEqualTo(Value.HIGH_IMPEDANCE);
        assertThat(engine.snapshot().problems()).hasSize(2).allMatch(ConnectivityProblem.class::isInstance);
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
        assertThat(engine.snapshot().shortCircuits()).isEmpty();
        assertThat(engine.snapshot().problems()).allMatch(ConnectivityProblem.class::isInstance);
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
        assertThat(engine.snapshot().widthMismatches()).singleElement()
                .satisfies(mismatch -> assertThat(mismatch.pins()).hasSize(2));
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

    private final List<Value> recorded = new ArrayList<>();
    private final AtomicInteger repeaterRuns = new AtomicInteger();

    private final ComponentType recorder = new ComponentType("test-recorder", ComponentCategory.DEBUG,
            new GridSize(2, 2), List.of(PinSpec.input("in", 0, 0)), List.of(),
            parameters -> (context, pinIndex) -> recorded.add(context.input(pinIndex)));

    /** Drives ONE on its output every unit, forever. */
    private final ComponentType repeater = new ComponentType("test-repeater", ComponentCategory.DEBUG,
            new GridSize(2, 2), List.of(PinSpec.output("out", 2, 0)), List.of(),
            parameters -> new Behavior() {
                @Override
                public void initialize(BehaviorContext context) {
                    repeat(context);
                }

                private void repeat(BehaviorContext context) {
                    repeaterRuns.incrementAndGet();
                    context.drive(0, Value.ONE, 0);
                    context.schedule(1, () -> repeat(context));
                }

                @Override
                public void inputChanged(BehaviorContext context, int pinIndex) {
                }
            });

    private FlatCircuit sourceAndRecorder(String value) {
        ComponentType source = registry.require(TestComponentTypes.SOURCE);
        return new FlatCircuit(List.of(new FlatComponent("s", source, Map.of("value", value)),
                new FlatComponent("r", recorder, Map.of())),
                List.of(new FlatNet("n", List.of(new FlatPinRef("r", 0), new FlatPinRef("s", 0)), 1, false)));
    }

    @Test
    void replacingADriverNeverExposesATransientHighImpedance() {
        engine.update(sourceAndRecorder("0"));
        assertThat(recorded).containsExactly(Value.ZERO);
        engine.update(sourceAndRecorder("1"));
        engine.advance(5);
        assertThat(recorded).containsExactly(Value.ZERO, Value.ONE);
    }

    @Test
    void inputsAreNotifiedOnlyWhenTheirValueChanges() {
        engine.update(new FlatCircuit(List.of(new FlatComponent("p", repeater, Map.of()),
                new FlatComponent("r", recorder, Map.of())),
                List.of(new FlatNet("n", List.of(new FlatPinRef("p", 0), new FlatPinRef("r", 0)), 1, false))));
        engine.advance(10);
        assertThat(repeaterRuns.get()).isGreaterThanOrEqualTo(10);
        assertThat(recorded).containsExactly(Value.ONE);
    }

    @Test
    void aRemovedSelfReschedulingBehaviorStopsRunning() {
        engine.update(new FlatCircuit(List.of(new FlatComponent("p", repeater, Map.of())), List.of()));
        engine.advance(5);
        int runsBeforeRemoval = repeaterRuns.get();
        assertThat(runsBeforeRemoval).isGreaterThanOrEqualTo(5);
        engine.update(FlatCircuit.EMPTY);
        engine.advance(20);
        assertThat(repeaterRuns).hasValue(runsBeforeRemoval);
    }

    @Test
    void shortCircuitKeepsItsStartTimeWhenTheNetGrows() {
        ElementId low = place(TestComponentTypes.SOURCE, 0, 0, Map.of("value", "0"));
        place(TestComponentTypes.SOURCE, 4, 0, Map.of("value", "1"));
        wire(2, 1, 6, 1);
        synchronize();
        engine.advance(10);
        wire(2, -4, 2, 1);
        synchronize();
        engine.advance(5);
        assertThat(engine.snapshot().shortCircuits()).singleElement().satisfies(shortCircuit -> {
            assertThat(shortCircuit.netId()).isEqualTo("n:p:" + low.value() + "#0");
            assertThat(shortCircuit.sinceTime()).isZero();
        });
    }
}
