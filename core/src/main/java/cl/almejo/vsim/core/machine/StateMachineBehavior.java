package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ClockEdgeDetector;
import cl.almejo.vsim.core.machine.MachineEvaluator.MachineStep;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Runs a «Máquina de estados» in the circuit (spec §1 «Simulation is the circuit's», §2.3). Pins in
 * {@link MachineBody} order: the inputs, {@code RST}, {@code CLK}, the outputs. On the clock edge the machine chooses
 * (falling by default, like the flip-flops; only a direct step between 0 and 1 counts, so a clock through Z or a
 * conflict makes none) it takes the arrow the inputs select ({@link MachineEvaluator}). {@code RST = 1} sends it to the
 * initial state at once and holds it there; an unconnected {@code RST} is 0. An input that is not 0 or 1 (Z,
 * a conflict) counts as 0 in the conditions; the visible state tells the ones the conditions read that were so at the
 * last edge, and a clock in Z or in conflict, for Problemas ({@link MachineChecks#runtime}). The outputs are the
 * current state's Moore outputs plus the Mealy outputs of the arrow today's inputs select, driven
 * {@link #OUTPUT_DELAY} after a change. A machine without states or without an initial state holds no state, drives
 * 0 and shows nothing. Starts in the initial state, so «Reiniciar», which recreates the behaviour, brings it back.
 * After the outputs comes the hidden state pin ({@link StateMachineTypes}): the current state's code, driven
 * {@link #OUTPUT_DELAY} after it changes (start, edge, {@code RST}, a rebuild that kept the state), or Z with no state.
 */
final class StateMachineBehavior implements Behavior {

    /** Like a flip-flop's default delay: the outputs change one unit after the edge or the input. */
    static final int OUTPUT_DELAY = 1;

    private final StateMachine machine;
    private final MachineEvaluator evaluator;
    private final int resetPin;
    private final int clockPin;
    private final ClockEdgeDetector edge;
    /** The hidden pin that carries the state code: its index in the native type ({@link StateMachineTypes}). */
    private final int statePin;
    private final int stateWidth;
    private Value drivenStateCode;
    private Optional<String> currentStateId;
    private Map<String, Boolean> inputs = Map.of();
    private boolean resetActive;
    /** The input pins a condition reads: only these are told when Z or in conflict at an edge. */
    private final List<String> inputsRead;
    /** Of {@link #inputsRead}, those that are Z or in conflict now, and those that were at the last edge. */
    private List<String> undefinedInputs = List.of();
    private List<String> undefinedInputsAtLastEdge = List.of();
    /**
     * Whether {@code CLK} is Z or in conflict now, and the context that tells whether anything drives it: asked at
     * each look ({@link #visibleState}), since joining or freeing {@code CLK} may leave its value as it was (a free
     * {@code CLK} is not told, the user's decision of 2026-11-15). The engine reuses the context for this copy.
     */
    private boolean clockValueUndefined;
    private Optional<BehaviorContext> clockContext = Optional.empty();
    private final Map<String, Boolean> driven = new HashMap<>();

    StateMachineBehavior(StateMachine machine, int statePin) {
        this.machine = machine;
        this.evaluator = MachineEvaluator.of(machine);
        this.resetPin = machine.inputNames().size();
        this.clockPin = resetPin + 1;
        this.edge = ClockEdgeDetector.forEdge(machine.edge());
        this.statePin = statePin;
        this.stateWidth = StateMachineTypes.stateWidth(machine);
        this.currentStateId = machine.initialStateId();
        this.inputsRead = machine.inputNames().stream().filter(machine.inputNamesUsed()::contains).toList();
    }

    @Override
    public void initialize(BehaviorContext context) {
        edge.start(context.input(clockPin));
        readInputs(context);
        if (resetActive) {
            currentStateId = machine.initialStateId();
        }
        driveOutputs(context, true);
    }

    @Override
    public void inputChanged(BehaviorContext context, int pinIndex) {
        readInputs(context);
        boolean edgeCame = pinIndex == clockPin && edge.step(context.input(clockPin));
        if (resetActive) {
            currentStateId = machine.initialStateId();
        } else if (edgeCame) {
            undefinedInputsAtLastEdge = undefinedInputs;
            currentStateId.flatMap(stateId -> evaluator.next(stateId, inputs))
                    .ifPresent(step -> currentStateId = Optional.of(step.nextStateId()));
        }
        driveOutputs(context, false);
    }

    /** Reads the inputs as the conditions see them (only 1 is true) and whether {@code RST} is 1. */
    private void readInputs(BehaviorContext context) {
        Map<String, Boolean> read = new HashMap<>();
        List<String> undefined = new ArrayList<>();
        List<String> names = machine.inputNames();
        for (int index = 0; index < names.size(); index++) {
            Value value = context.input(index);
            read.put(names.get(index), value.equals(Value.ONE));
            if (!value.isFullyDefined() && inputsRead.contains(names.get(index))) {
                undefined.add(names.get(index));
            }
        }
        inputs = read;
        undefinedInputs = List.copyOf(undefined);
        resetActive = context.input(resetPin).equals(Value.ONE);
        clockValueUndefined = !context.input(clockPin).isFullyDefined();
        clockContext = Optional.of(context);
    }

    /** Drives every output whose value changed (all of them when {@code everyOutput}, at the start). */
    private void driveOutputs(BehaviorContext context, boolean everyOutput) {
        Map<String, Boolean> values = currentStateId.map(stateId -> evaluator.outputs(stateId, inputs))
                .orElseGet(this::allZero);
        List<String> outputs = machine.outputNames();
        for (int index = 0; index < outputs.size(); index++) {
            String output = outputs.get(index);
            boolean value = values.getOrDefault(output, false);
            Boolean before = driven.put(output, value);
            if (everyOutput || before == null || before != value) {
                context.drive(clockPin + 1 + index, value ? Value.ONE : Value.ZERO, OUTPUT_DELAY);
            }
        }
        driveStateCode(context, everyOutput);
    }

    /** Drives the current state's code on the hidden state pin when it changed, or Z while there is no state. */
    private void driveStateCode(BehaviorContext context, boolean always) {
        OptionalInt index = currentStateId.map(stateId -> StateMachineTypes.stateCode(machine, stateId))
                .orElse(OptionalInt.empty());
        Value code = index.isPresent() ? Value.of(stateWidth, index.getAsInt()) : Value.highImpedance(stateWidth);
        if (always || !code.equals(drivenStateCode)) {
            drivenStateCode = code;
            context.drive(statePin, code, OUTPUT_DELAY);
        }
    }

    private Map<String, Boolean> allZero() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        machine.outputNames().forEach(output -> values.put(output, false));
        return values;
    }

    /**
     * The current state and where the next edge takes it with today's inputs; {@link VisibleState#NONE} while the
     * machine holds no state.
     */
    @Override
    public VisibleState visibleState() {
        Optional<MachineState> current = currentStateId.flatMap(machine::state);
        if (current.isEmpty()) {
            return VisibleState.NONE;
        }
        MachineState state = current.get();
        if (resetActive) {
            return new VisibleState.MachineVisibleState(state.id(), state.name(), Optional.empty(), Optional.empty(),
                    undefinedInputsAtLastEdge, clockUndefined());
        }
        Optional<MachineStep> step = evaluator.next(state.id(), inputs);
        return new VisibleState.MachineVisibleState(state.id(), state.name(), step.map(MachineStep::nextStateId),
                step.flatMap(MachineStep::transitionId), undefinedInputsAtLastEdge, clockUndefined());
    }

    /** Whether something drives {@code CLK} Z or in conflict now. */
    private boolean clockUndefined() {
        return clockValueUndefined && clockContext.filter(context -> context.isDriven(clockPin)).isPresent();
    }

    /**
     * A rebuild after an edit of the machine (spec §2.3): the new behaviour keeps the state the old one was in when
     * that state still exists (same id), else it starts in the initial state.
     */
    @Override
    public void adoptStateFrom(Behavior previous) {
        if (previous instanceof StateMachineBehavior running) {
            running.currentStateId.filter(stateId -> machine.state(stateId).isPresent())
                    .ifPresent(stateId -> currentStateId = Optional.of(stateId));
        }
    }
}
