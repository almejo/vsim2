package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.MachineCopies;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.MachineProblem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A state machine's row of the Problemas list for the AI: kind {@code MACHINE_<KIND>}, pointed at the copy it is about
 * (the running copy, else the machine's first one), with an English sentence that names the machine, its states and
 * arrows as the editor's row does.
 */
final class MachineProblemViews {

    private MachineProblemViews() {
    }

    static String kindOf(MachineProblem problem) {
        return "MACHINE_" + problem.finding().kind().name();
    }

    static ProblemView view(ReadSource source, MachineProblem problem) {
        ProblemReferences references = new ProblemReferences(source);
        problem.instancePath().or(() -> MachineCopies.firstPathOf(source.document(), problem.definitionId()))
                .ifPresent(references::element);
        Optional<ComponentDefinition> definition = source.document().definition(problem.definitionId());
        String name = definition.map(ComponentDefinition::name).orElse(problem.definitionId());
        StateMachine machine = definition.flatMap(ComponentDefinition::machine).orElse(StateMachine.initial());
        String where = problem.instancePath().map(path -> " (copy " + path + ")").orElse("");
        return references.view(kindOf(problem), problem.isWarning(), problem.netId(),
                "State machine \"" + name + "\"" + where + ": " + sentence(problem.finding(), machine));
    }

    private static String sentence(MachineFinding finding, StateMachine machine) {
        String states = String.join(", ", finding.stateIds().stream().map(id -> stateName(machine, id)).toList());
        String arrows = String.join(", ", finding.transitionIds().stream().map(id -> arrow(machine, id)).toList());
        String names = String.join(", ", finding.names());
        return switch (finding.kind()) {
            case NO_STATES -> "it has no states";
            case NO_INITIAL_STATE -> "no state is marked as initial, so it holds no state and drives 0";
            case BAD_CONDITION -> "the condition of " + arrows + " cannot be read (column " + (finding.column() + 1)
                    + "); the arrow is never taken";
            case TWO_OTHERWISE -> "state " + states + " has several arrows without a condition: " + arrows;
            case NONDETERMINISTIC -> "from " + states + " the arrows " + arrows + " hold at once with "
                    + values(finding.witness());
            case READS_OUTPUT_OR_RESERVED -> "the condition of " + arrows + " reads " + names
                    + ", which is not an input; it reads as 0";
            case TOO_MANY_NAMES -> names + " is not a pin: the machine already has 16 inputs or 16 outputs";
            case UNREACHABLE_STATE -> "state " + states + " cannot be reached from the initial state";
            case STAYS_FOR_SOME_INPUT -> "state " + states + " says nothing for " + values(finding.witness())
                    + ", so it stays there";
            case OUTPUT_NEVER_ASSIGNED -> "no state or arrow gives the output " + names + " a value: it is always 0";
            case UNUSED_INPUT -> "the input " + names + " is not used in any condition";
            case MOORE_MEALY_CLASH -> arrows + " gives " + names + " another value than state " + states
                    + ": the arrow wins";
            case INPUT_UNDEFINED_AT_EDGE -> names + " was Z or in conflict at the last clock edge and read as 0";
            case CLOCK_UNDEFINED -> "CLK is Z or in conflict: the machine does not advance";
        };
    }

    private static String stateName(StateMachine machine, String stateId) {
        return machine.state(stateId).map(MachineState::name).orElse(stateId);
    }

    private static String arrow(StateMachine machine, String transitionId) {
        return machine.transition(transitionId)
                .map(transition -> "the arrow " + stateName(machine, transition.fromId()) + " -> "
                        + stateName(machine, transition.toId()) + " \"" + transition.condition() + "\"")
                .orElse(transitionId);
    }

    private static String values(Map<String, Boolean> witness) {
        List<String> parts = new ArrayList<>();
        witness.forEach((input, value) -> parts.add(input + " = " + (value ? 1 : 0)));
        return parts.isEmpty() ? "every input" : String.join(", ", parts);
    }
}
