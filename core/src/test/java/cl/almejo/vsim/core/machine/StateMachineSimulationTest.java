package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.MachineVisibleState;
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
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.Trace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** A machine runs natively in the circuit: switches, clock and LED around the mockup's detector of two 1s. */
class StateMachineSimulationTest {

    private static final String MACHINE_ID = "maquina-1";
    /** Long enough for every delay after an input change to settle. */
    private static final long SETTLE = 20;
    /** A switch starts at 1 by default; the tests start every input at 0. */
    private static final Map<String, Object> SWITCH_OFF = Map.of("value", "0");

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final SimulationEngine engine = new SimulationEngine();
    private int nextWire = 100;

    StateMachineSimulationTest() {
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    @Test
    void theDetectorFollowsTheMockupsSequenceOnTheFallingEdge() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();

        List<String> states = new ArrayList<>();
        List<Value> lamp = new ArrayList<>();
        for (boolean x : List.of(false, true, true, true, false, true)) {
            set("x", x);
            fallingEdge("clk");
            states.add(stateName("m"));
            lamp.add(led("z"));
        }

        assertThat(states).containsExactly("S0", "S1", "S2", "S2", "S0", "S1");
        assertThat(lamp).containsExactly(Value.ZERO, Value.ZERO, Value.ONE, Value.ONE, Value.ZERO, Value.ZERO);
    }

    @Test
    void aRisingClockDoesNothingByDefault() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        set("x", true);

        set("clk", true);

        assertThat(stateName("m")).isEqualTo("S0");
    }

    @Test
    void aMachineOnTheRisingEdgeChangesWhenTheClockRises() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector().withEdge(StateMachine.EDGE_RISING));
        start();
        set("x", true);

        set("clk", true);
        assertThat(stateName("m")).isEqualTo("S1");
        set("clk", false);
        assertThat(stateName("m")).isEqualTo("S1");
    }

    @Test
    void resetGoesToTheInitialStateAtOnceAndHoldsItThere() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        set("x", true);
        fallingEdge("clk");
        fallingEdge("clk");
        assertThat(stateName("m")).isEqualTo("S2");

        set("rst", true);
        assertThat(stateName("m")).isEqualTo("S0");
        assertThat(led("z")).isEqualTo(Value.ZERO);
        fallingEdge("clk");
        assertThat(stateName("m")).isEqualTo("S0");

        set("rst", false);
        fallingEdge("clk");
        assertThat(stateName("m")).isEqualTo("S1");
    }

    @Test
    void aClockThroughHighImpedanceMakesNoEdge() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        set("x", true);
        set("clk", true);

        drive("clk", Value.HIGH_IMPEDANCE);
        drive("clk", Value.ZERO);

        assertThat(stateName("m")).isEqualTo("S0");
    }

    @Test
    void anInputInHighImpedanceCountsAsZero() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        set("x", true);
        fallingEdge("clk");
        assertThat(stateName("m")).isEqualTo("S1");

        drive("x", Value.HIGH_IMPEDANCE);
        fallingEdge("clk");

        assertThat(stateName("m")).as("x counts as 0: S1's «otherwise» arrow").isEqualTo("S0");
    }

    @Test
    void anInputInHighImpedanceAtTheEdgeIsToldUntilTheNextEdge() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        assertThat(machineState("m").undefinedInputsAtLastEdge()).isEmpty();

        drive("x", Value.HIGH_IMPEDANCE);
        assertThat(machineState("m").undefinedInputsAtLastEdge()).as("no edge yet").isEmpty();
        fallingEdge("clk");
        assertThat(machineState("m").undefinedInputsAtLastEdge()).containsExactly("x");

        set("x", true);
        assertThat(machineState("m").undefinedInputsAtLastEdge()).as("still the last edge's").containsExactly("x");
        fallingEdge("clk");
        assertThat(machineState("m").undefinedInputsAtLastEdge()).isEmpty();
    }

    @Test
    void aClockInHighImpedanceIsToldWhileItLasts() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        assertThat(machineState("m").clockUndefined()).isFalse();

        drive("clk", Value.HIGH_IMPEDANCE);
        assertThat(machineState("m").clockUndefined()).isTrue();

        set("clk", false);
        assertThat(machineState("m").clockUndefined()).isFalse();
    }

    /** The user's decision (hand test of hito 2): a just placed machine, its CLK free, gives no warning. */
    @Test
    void anUnconnectedClockIsNotTold() {
        document.putDefinition(MachineDefinitions.create(MACHINE_ID, "Máquina 1")
                .withMachine(MachineExamples.twoOnesDetector()));
        machinePiece(document, "m", new GridPoint(20, 0));
        start();

        assertThat(machineState("m").clockUndefined()).isFalse();
    }

    @Test
    void theNextArrowFollowsTheInputs() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        set("x", true);
        fallingEdge("clk");

        MachineVisibleState state = machineState("m");
        assertThat(state.stateId()).isEqualTo("s1");
        assertThat(state.nextStateId()).contains("s2");
        assertThat(state.nextTransitionId()).contains("t2");

        set("x", false);
        assertThat(machineState("m").nextTransitionId()).contains("t3");
    }

    @Test
    void aMealyOutputFollowsTheInputWithoutAClockEdge() {
        StateMachine mealy = new StateMachine(
                List.of(MachineExamples.state("s0", "S0", 0, List.of()),
                        MachineExamples.state("s1", "S1", 100, List.of())),
                List.of(MachineExamples.arrow("t0", "s0", "s1", ""),
                        new MachineTransition("t1", "s1", "s1", "x", List.of(new OutputAssignment("Z", true))),
                        MachineExamples.arrow("t2", "s1", "s1", "")),
                java.util.Optional.of("s0"), List.of("x"), List.of("Z"), false, false, StateMachine.EDGE_FALLING,
                List.of());
        detectorWithSwitchesAndLed(mealy);
        start();
        fallingEdge("clk");
        assertThat(stateName("m")).isEqualTo("S1");
        assertThat(led("z")).as("x is 0: the otherwise arrow assigns nothing").isEqualTo(Value.ZERO);

        set("x", true);
        assertThat(led("z")).as("no edge, and Z follows x").isEqualTo(Value.ONE);
        set("x", false);
        assertThat(led("z")).isEqualTo(Value.ZERO);
    }

    @Test
    void anEditKeepsTheCurrentStateWhenItStillExists() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        set("x", true);
        fallingEdge("clk");
        fallingEdge("clk");
        History history = new History(document);
        StateMachine renamed = MachineExamples.twoOnesDetector()
                .withState("s2", state -> state.withName("Dos"));

        history.execute(new EditMachineCommand(MACHINE_ID, renamed));
        rebuild();

        assertThat(machineState("m").stateId()).isEqualTo("s2");
        assertThat(stateName("m")).isEqualTo("Dos");
        assertThat(led("z")).isEqualTo(Value.ONE);
    }

    @Test
    void anEditThatRemovesTheCurrentStateGoesToTheInitialOne() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        set("x", true);
        fallingEdge("clk");
        fallingEdge("clk");
        StateMachine detector = MachineExamples.twoOnesDetector();
        StateMachine withoutS2 = detector
                .withTransitions(detector.transitions().stream()
                        .filter(transition -> !transition.fromId().equals("s2") && !transition.toId().equals("s2"))
                        .toList())
                .withStates(detector.states().subList(0, 2));

        new History(document).execute(new EditMachineCommand(MACHINE_ID, withoutS2));
        rebuild();

        assertThat(stateName("m")).isEqualTo("S0");
    }

    @Test
    void restartingGoesBackToTheInitialState() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        set("x", true);
        fallingEdge("clk");

        engine.restart(flatten(), Map.of());
        engine.advance(SETTLE);

        assertThat(stateName("m")).isEqualTo("S0");
    }

    @Test
    void twoCopiesKeepTheirOwnStates() {
        Component first = detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        Component second = machinePiece(document, "m2", new GridPoint(60, 0));
        feed(document, "clk2", StandardComponentTypes.SWITCH, second, MachineLimits.CLOCK);
        start();

        set("x", true);
        fallingEdge("clk");
        fallingEdge("clk2");

        assertThat(stateName(first.id().value())).isEqualTo("S1");
        assertThat(stateName("m2")).as("its x is not connected: 0").isEqualTo("S0");
    }

    @Test
    void aMachineInsideAUserComponentRunsWithItsPath() {
        document.putDefinition(MachineDefinitions.create(MACHINE_ID, "Máquina 1")
                .withMachine(MachineExamples.twoOnesDetector()));
        document.putDefinition(wrapper());
        Component piece = new Component(new ElementId("w"), SubcircuitTypes.typeIdOf("wrapper"),
                new GridPoint(20, 0), Rotation.R0, Map.of(), "");
        document.add(piece);
        feed(document, "x", StandardComponentTypes.SWITCH, piece, "x");
        feed(document, "clk", StandardComponentTypes.SWITCH, piece, MachineLimits.CLOCK);
        drain(document, "z", StandardComponentTypes.LED, piece, "Z");
        start();

        set("x", true);
        fallingEdge("clk");
        fallingEdge("clk");

        assertThat(stateName("w/m")).isEqualTo("S2");
        assertThat(led("z")).isEqualTo(Value.ONE);
    }

    // --- the state channel ---

    @Test
    void theStateNetCarriesTheStateCodeAlongTheMockupsSequence() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        engine.update(flatten(), Map.of("estado", StateMachineTypes.stateNetId("m")));
        engine.advance(SETTLE);

        List<Long> codes = new ArrayList<>();
        for (boolean x : List.of(false, true, true, true, false, true)) {
            set("x", x);
            fallingEdge("clk");
            codes.add(stateCode("m").bits());
        }

        assertThat(codes).containsExactly(0L, 1L, 2L, 2L, 0L, 1L);
        Trace trace = engine.waveforms().trace("estado").orElseThrow();
        assertThat(trace.width()).isEqualTo(2);
        assertThat(definedValues(trace)).containsExactly(0L, 1L, 2L, 0L, 1L);
    }

    @Test
    void aMachineInsideAUserComponentHasItsOwnStateNet() {
        document.putDefinition(MachineDefinitions.create(MACHINE_ID, "Máquina 1")
                .withMachine(MachineExamples.twoOnesDetector()));
        document.putDefinition(wrapper());
        Component piece = new Component(new ElementId("w"), SubcircuitTypes.typeIdOf("wrapper"),
                new GridPoint(20, 0), Rotation.R0, Map.of(), "");
        document.add(piece);
        feed(document, "x", StandardComponentTypes.SWITCH, piece, "x");
        feed(document, "clk", StandardComponentTypes.SWITCH, piece, MachineLimits.CLOCK);
        start();

        set("x", true);
        fallingEdge("clk");
        fallingEdge("clk");

        assertThat(stateCode("w/m")).isEqualTo(Value.of(2, 2));
    }

    @Test
    void theStateNetCausesNoProblem() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        machinePiece(document, "loose", new GridPoint(60, 0));
        FlatCircuit circuit = flatten();
        engine.update(circuit);
        engine.advance(SETTLE);
        fallingEdge("clk");

        assertThat(circuit.connectivityProblems()).noneMatch(problem -> problem.netId().contains("#"));
        assertThat(circuit.nets()).filteredOn(net -> net.id().equals(StateMachineTypes.stateNetId("loose")))
                .singleElement().satisfies(net -> assertThat(net.widthMismatch()).isFalse());
        assertThat(engine.snapshot().problems()).isEmpty();
    }

    @Test
    void fiveStatesNeedThreeBits() {
        detectorWithSwitchesAndLed(withExtraStates(MachineExamples.twoOnesDetector(), 2));
        start();

        assertThat(stateCode("m")).isEqualTo(Value.of(3, 0));
        assertThat(StateMachineTypes.stateWidth(withExtraStates(MachineExamples.twoOnesDetector(), 2))).isEqualTo(3);
        assertThat(StateMachineTypes.stateWidth(withExtraStates(MachineExamples.twoOnesDetector(), 1))).isEqualTo(2);
        assertThat(StateMachineTypes.stateWidth(StateMachine.initial())).isEqualTo(1);
    }

    @Test
    void aMachineWithoutStatesDrivesHighImpedanceOnItsStateNet() {
        StateMachine empty = MachineExamples.twoOnesDetector().withTransitions(List.of())
                .withInitialStateId(Optional.empty()).withStates(List.of());
        detectorWithSwitchesAndLed(empty);
        start();

        assertThat(flatten().netOfPin("m", 4)).as("the hidden pin after x, RST, CLK and Z")
                .contains(StateMachineTypes.stateNetId("m"));
        assertThat(stateCode("m").isFullyHighImpedance()).isTrue();
    }

    @Test
    void resetDrivesTheInitialStatesCode() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        start();
        set("x", true);
        fallingEdge("clk");
        fallingEdge("clk");

        set("rst", true);

        assertThat(stateCode("m")).isEqualTo(Value.of(2, 0));
    }

    @Test
    void anEditThatWidensTheStateKeepsTheChannelBound() {
        detectorWithSwitchesAndLed(MachineExamples.twoOnesDetector());
        Map<String, String> channels = Map.of("estado", StateMachineTypes.stateNetId("m"));
        engine.update(flatten(), channels);
        engine.advance(SETTLE);
        set("x", true);
        fallingEdge("clk");
        fallingEdge("clk");

        new History(document).execute(new EditMachineCommand(MACHINE_ID,
                withExtraStates(MachineExamples.twoOnesDetector(), 2)));
        engine.update(flatten(), channels);
        engine.advance(SETTLE);

        assertThat(stateCode("m")).as("S2 kept, now 3 bits").isEqualTo(Value.of(3, 2));
        Trace trace = engine.waveforms().trace("estado").orElseThrow();
        assertThat(trace.width()).isEqualTo(3);
        assertThat(trace.valueAt(engine.now())).contains(Value.of(3, 2));
    }

    @Test
    void theStateCodeIsTheIndexInTheStates() {
        StateMachine detector = MachineExamples.twoOnesDetector();

        assertThat(StateMachineTypes.stateCode(detector, "s2")).hasValue(2);
        assertThat(StateMachineTypes.stateCode(detector, "nope")).isEmpty();
        assertThat(StateMachineTypes.stateIdOfCode(detector, 1)).contains("s1");
        assertThat(StateMachineTypes.stateIdOfCode(detector, 3)).isEmpty();
        assertThat(StateMachineTypes.stateIdOfCode(detector, -1)).isEmpty();
    }

    /** {@code machine} with {@code count} more states, {@code E0}, {@code E1}…, with no arrows. */
    private static StateMachine withExtraStates(StateMachine machine, int count) {
        List<MachineState> states = new ArrayList<>(machine.states());
        for (int index = 0; index < count; index++) {
            states.add(MachineExamples.state("e" + index, "E" + index, 300 + 100 * index, List.of()));
        }
        return machine.withStates(states);
    }

    /** The defined values {@code trace} took, in order, without its repeats or gaps. */
    private static List<Long> definedValues(Trace trace) {
        List<Long> values = new ArrayList<>();
        for (int index = 0; index < trace.eventCount(); index++) {
            trace.eventValue(index).filter(Value::isFullyDefined).ifPresent(value -> {
                if (values.isEmpty() || values.get(values.size() - 1) != value.bits()) {
                    values.add(value.bits());
                }
            });
        }
        return values;
    }

    // --- the circuit ---

    /** The machine as piece {@code m}, switches {@code x}, {@code rst} and {@code clk}, and LED {@code z}. */
    private Component detectorWithSwitchesAndLed(StateMachine machine) {
        document.putDefinition(MachineDefinitions.create(MACHINE_ID, "Máquina 1").withMachine(machine));
        Component piece = machinePiece(document, "m", new GridPoint(20, 0));
        feed(document, "x", StandardComponentTypes.SWITCH, piece, "x");
        feed(document, "rst", StandardComponentTypes.SWITCH, piece, MachineLimits.RESET);
        feed(document, "clk", StandardComponentTypes.SWITCH, piece, MachineLimits.CLOCK);
        drain(document, "z", StandardComponentTypes.LED, piece, "Z");
        return piece;
    }

    /** A user component whose body is the machine with its x, CLK and Z brought out (RST left unconnected). */
    private ComponentDefinition wrapper() {
        CircuitDocument body = new CircuitDocument();
        Component machine = machinePiece(body, "m", new GridPoint(20, 0));
        Component x = feed(body, "px", InterfacePins.INPUT_PIN, machine, "x");
        Component clock = feed(body, "pclk", InterfacePins.INPUT_PIN, machine, MachineLimits.CLOCK);
        Component z = drain(body, "pz", InterfacePins.OUTPUT_PIN, machine, "Z");
        List<DefinitionPin> pinout = List.of(
                new DefinitionPin(PinSpec.input("x", 0, 2, 1), x.id()),
                new DefinitionPin(PinSpec.input(MachineLimits.CLOCK, 0, 4, 1), clock.id()),
                new DefinitionPin(PinSpec.output("Z", 6, 2, 1), z.id()));
        return new ComponentDefinition("wrapper", "Envoltorio", "", new GridSize(6, 6), pinout,
                new CircuitFragment(body.components(), body.wires(), List.of()));
    }

    private Component machinePiece(CircuitDocument target, String id, GridPoint position) {
        Component piece = new Component(new ElementId(id), SubcircuitTypes.typeIdOf(MACHINE_ID), position,
                Rotation.R0, Map.of(), "");
        target.add(piece);
        return piece;
    }

    /** A part of {@code typeId} whose pin 0 drives {@code pinName} of {@code piece} through a wire from the left. */
    private Component feed(CircuitDocument target, String id, String typeId, Component piece, String pinName) {
        return attach(target, id, typeId, piece, pinName, -6);
    }

    /** A part of {@code typeId} whose pin 0 reads {@code pinName} of {@code piece} through a wire to the right. */
    private Component drain(CircuitDocument target, String id, String typeId, Component piece, String pinName) {
        return attach(target, id, typeId, piece, pinName, 6);
    }

    private Component attach(CircuitDocument target, String id, String typeId, Component piece, String pinName,
                             int distance) {
        GridPoint pin = pinOf(target, piece, pinName);
        Component probe = registry.require(typeId).newComponent(new ElementId(id), GridPoint.ORIGIN, Rotation.R0,
                Map.of(), "");
        GridPoint offset = registry.typeOf(probe).pinLocation(probe, 0);
        GridPoint end = pin.translate(distance, 0);
        Map<String, Object> parameters = typeId.equals(StandardComponentTypes.SWITCH) ? SWITCH_OFF : Map.of();
        Component placed = registry.require(typeId).newComponent(new ElementId(id),
                new GridPoint(end.x() - offset.x(), end.y() - offset.y()), Rotation.R0, parameters, "");
        target.add(placed);
        target.add(new Wire(new ElementId("w" + nextWire++), pin, end));
        return placed;
    }

    private GridPoint pinOf(CircuitDocument target, Component piece, String pinName) {
        ComponentDefinition definition = document.definition(SubcircuitTypes.definitionIdOf(piece.typeId())
                .orElseThrow()).orElseThrow();
        int index = definition.pinIndexOf(pinName).orElseThrow();
        return registry.typeOf(piece).pinLocation(piece, index);
    }

    // --- running it ---

    private FlatCircuit flatten() {
        return new CircuitFlattener(registry).flatten(document, new NetlistCompiler(registry).compile(document));
    }

    private void start() {
        engine.update(flatten());
        engine.advance(SETTLE);
    }

    private void rebuild() {
        engine.update(flatten());
        engine.advance(SETTLE);
    }

    private void set(String switchId, boolean value) {
        drive(switchId, value ? Value.ONE : Value.ZERO);
    }

    private void drive(String switchId, Value value) {
        engine.setInput(switchId, value);
        engine.advance(SETTLE);
    }

    private void fallingEdge(String clockId) {
        set(clockId, true);
        set(clockId, false);
    }

    private MachineVisibleState machineState(String flatId) {
        VisibleState state = engine.snapshot().componentState(flatId);
        assertThat(state).as(flatId).isInstanceOf(MachineVisibleState.class);
        return (MachineVisibleState) state;
    }

    private Value stateCode(String flatId) {
        return engine.netValue(StateMachineTypes.stateNetId(flatId));
    }

    private String stateName(String flatId) {
        return machineState(flatId).stateName();
    }

    private Value led(String id) {
        return ((VisibleState.LedState) engine.snapshot().componentState(id)).value();
    }
}
