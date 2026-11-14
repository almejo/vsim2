package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.core.io.CircuitFormatException.invalid;

/**
 * The JSON shape of a «Máquina de estados» (format 7, the {@code machine} of a definition entry): states with their
 * place and Moore outputs, arrows with their condition as typed and their Mealy outputs, the initial state, the pin
 * names in order, the bus flags, the edge and the saved tests. Empty lists and the defaults of the flags are not
 * written. An output's value is 0 or 1.
 */
final class MachineEntries {

    record MachineEntry(List<StateEntry> states,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TransitionEntry> transitions,
                        @JsonInclude(JsonInclude.Include.NON_NULL) String initialState,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> inputs,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> outputs,
                        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean inputsAsBus,
                        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean outputsAsBus,
                        String edge,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TestEntry> tests) {
    }

    record StateEntry(String id, String name, double x, double y,
                      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<OutputEntry> outputs) {
    }

    record TransitionEntry(String id, String from, String to, String condition,
                           @JsonInclude(JsonInclude.Include.NON_EMPTY) List<OutputEntry> outputs) {
    }

    record OutputEntry(String output, int value) {
    }

    record TestEntry(String name) {
    }

    private MachineEntries() {
    }

    static MachineEntry entry(StateMachine machine) {
        return new MachineEntry(
                machine.states().stream().map(state -> new StateEntry(state.id(), state.name(),
                        state.position().x(), state.position().y(), outputEntries(state.outputs()))).toList(),
                machine.transitions().stream().map(transition -> new TransitionEntry(transition.id(),
                        transition.fromId(), transition.toId(), transition.condition(),
                        outputEntries(transition.outputs()))).toList(),
                machine.initialStateId().orElse(null), machine.inputNames(), machine.outputNames(),
                machine.inputsAsBus(), machine.outputsAsBus(), machine.edge(),
                machine.tests().stream().map(test -> new TestEntry(test.name())).toList());
    }

    private static List<OutputEntry> outputEntries(List<OutputAssignment> assignments) {
        return assignments.stream()
                .map(assignment -> new OutputEntry(assignment.output(), assignment.value() ? 1 : 0)).toList();
    }

    /** The machine {@code entry} describes; a missing or malformed part is an invalid file. */
    static StateMachine machine(MachineEntry entry, String definitionId) throws CircuitFormatException {
        try {
            List<MachineState> states = new ArrayList<>();
            for (StateEntry state : CircuitFormat.entries(entry.states())) {
                CircuitFormat.requireEntry(state, "states of " + definitionId);
                states.add(new MachineState(state.id(), state.name(), new DiagramPoint(state.x(), state.y()),
                        outputs(state.outputs(), definitionId)));
            }
            List<MachineTransition> transitions = new ArrayList<>();
            for (TransitionEntry transition : CircuitFormat.entries(entry.transitions())) {
                CircuitFormat.requireEntry(transition, "transitions of " + definitionId);
                transitions.add(new MachineTransition(transition.id(), transition.from(), transition.to(),
                        transition.condition(), outputs(transition.outputs(), definitionId)));
            }
            List<MachineTest> tests = new ArrayList<>();
            for (TestEntry test : CircuitFormat.entries(entry.tests())) {
                tests.add(new MachineTest(CircuitFormat.requireEntry(test, "tests of " + definitionId).name()));
            }
            String edge = entry.edge() == null ? StateMachine.EDGE_FALLING : entry.edge();
            return new StateMachine(states, transitions, Optional.ofNullable(entry.initialState()),
                    CircuitFormat.entries(entry.inputs()), CircuitFormat.entries(entry.outputs()),
                    entry.inputsAsBus(), entry.outputsAsBus(), edge, tests);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid("machine of " + definitionId + ": " + exception.getMessage(), exception);
        }
    }

    private static List<OutputAssignment> outputs(List<OutputEntry> entries, String definitionId)
            throws CircuitFormatException {
        List<OutputAssignment> outputs = new ArrayList<>();
        for (OutputEntry entry : CircuitFormat.entries(entries)) {
            CircuitFormat.requireEntry(entry, "outputs of " + definitionId);
            if (entry.value() != 0 && entry.value() != 1) {
                throw invalid("machine of " + definitionId + ": output " + entry.output() + " must be 0 or 1");
            }
            outputs.add(new OutputAssignment(entry.output(), entry.value() == 1));
        }
        return outputs;
    }
}
