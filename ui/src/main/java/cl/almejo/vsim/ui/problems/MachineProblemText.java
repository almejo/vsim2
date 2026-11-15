package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.logic.ExpressionProblem;
import cl.almejo.vsim.core.machine.MachineConditions;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.InstanceNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The words of a state machine's problems (hito 2, as in the mockup's «Los problemas, a la vista»): {@link #sentence}
 * says what is wrong inside the machine («El estado C no se alcanza desde el inicio.»), with the states and arrows by
 * their names; {@link #describe} adds which machine, and which copy for the problems of a running copy, for the
 * Problemas list of the whole circuit.
 */
public final class MachineProblemText {

    private static final String LIST_SEPARATOR = ", ";

    private MachineProblemText() {
    }

    /** «Máquina «Detector»: …», or «Máquina «Detector» en U1 › U3: …» for a running copy. */
    public static String describe(MachineProblem problem, CircuitDocument document) {
        Optional<ComponentDefinition> definition = document.definition(problem.definitionId());
        String machineName = definition.map(ComponentDefinition::name).orElse(problem.definitionId());
        StateMachine machine = definition.flatMap(ComponentDefinition::machine).orElse(StateMachine.initial());
        String sentence = sentence(problem.finding(), machine);
        return problem.instancePath()
                .map(path -> Messages.format("problem.machine.ofCopy", machineName,
                        InstanceNames.path(path, document).orElse(path), sentence))
                .orElseGet(() -> Messages.format("problem.machine.ofMachine", machineName, sentence));
    }

    /** What {@code finding} says about {@code machine}, without naming the machine. */
    public static String sentence(MachineFinding finding, StateMachine machine) {
        return switch (finding.kind()) {
            case NO_STATES -> Messages.text("problem.machine.noStates");
            case NO_INITIAL_STATE -> Messages.text("problem.machine.noInitialState");
            case BAD_CONDITION -> badCondition(finding, machine);
            case TWO_OTHERWISE -> Messages.format("problem.machine.twoOtherwise", stateName(machine, finding),
                    finding.transitionIds().size());
            case NONDETERMINISTIC -> nondeterministic(finding, machine);
            case READS_OUTPUT_OR_RESERVED -> arrowWords(machine, finding, "problem.machine.readsOutputOrReserved",
                    String.join(LIST_SEPARATOR, finding.names()));
            case TOO_MANY_NAMES -> tooManyNames(finding, machine);
            case UNREACHABLE_STATE -> Messages.format("problem.machine.unreachable", stateName(machine, finding));
            case STAYS_FOR_SOME_INPUT -> finding.witness().isEmpty()
                    ? Messages.format("problem.machine.staysAlways", stateName(machine, finding))
                    : Messages.format("problem.machine.stays", stateName(machine, finding),
                    values(finding.witness()));
            case OUTPUT_NEVER_ASSIGNED -> Messages.format("problem.machine.outputNeverAssigned", firstName(finding));
            case UNUSED_INPUT -> Messages.format("problem.machine.unusedInput", firstName(finding));
            case MOORE_MEALY_CLASH -> mooreMealyClash(finding, machine);
            case INPUT_UNDEFINED_AT_EDGE -> Messages.format("problem.machine.inputUndefined",
                    String.join(LIST_SEPARATOR, finding.names()));
            case CLOCK_UNDEFINED -> Messages.format("problem.machine.clockUndefined", MachineLimits.CLOCK);
        };
    }

    private static String badCondition(MachineFinding finding, StateMachine machine) {
        Optional<MachineTransition> transition = firstTransition(machine, finding);
        String condition = transition.map(MachineTransition::condition).orElse("");
        Optional<ExpressionProblem> problem = MachineConditions.parse(condition).problem();
        String reason = problem.map(found -> Messages.format(found.messageKey(), found.arguments().toArray()))
                .orElse("");
        if (finding.column() < 0) {
            return arrowWords(machine, finding, "problem.machine.badConditionNoColumn", reason);
        }
        return arrowWords(machine, finding, "problem.machine.badCondition", finding.column() + 1, reason);
    }

    private static String nondeterministic(MachineFinding finding, StateMachine machine) {
        List<String> conditions = finding.transitionIds().stream()
                .map(id -> machine.transition(id).map(MachineTransition::condition).orElse(id)).toList();
        String first = conditions.isEmpty() ? "" : conditions.getFirst();
        String second = conditions.size() < 2 ? "" : conditions.get(1);
        if (finding.witness().isEmpty()) {
            return Messages.format("problem.machine.nondeterministicAlways", stateName(machine, finding), first,
                    second);
        }
        return Messages.format("problem.machine.nondeterministic", stateName(machine, finding), first, second,
                values(finding.witness()));
    }

    private static String tooManyNames(MachineFinding finding, StateMachine machine) {
        String name = firstName(finding);
        return machine.outputNamesUsed().contains(name)
                ? Messages.format("problem.machine.tooManyOutputs", name, MachineLimits.MAXIMUM_OUTPUTS)
                : Messages.format("problem.machine.tooManyInputs", name, MachineLimits.MAXIMUM_INPUTS);
    }

    private static String mooreMealyClash(MachineFinding finding, StateMachine machine) {
        String output = firstName(finding);
        Optional<MachineTransition> transition = firstTransition(machine, finding);
        String arrowValue = transition.flatMap(found -> valueOf(found.outputs(), output)).orElse("");
        String stateValue = transition.flatMap(found -> machine.state(found.fromId()))
                .flatMap(state -> valueOf(state.outputs(), output)).orElse("");
        return arrowWords(machine, finding, "problem.machine.mooreMealyClash", output, arrowValue, stateValue);
    }

    private static Optional<String> valueOf(List<OutputAssignment> assignments, String output) {
        return assignments.stream().filter(assignment -> assignment.output().equals(output)).findFirst()
                .map(assignment -> assignment.value() ? "1" : "0");
    }

    /** A sentence about the finding's first arrow: {0} its condition, {1} and {2} its ends, then {@code more}. */
    private static String arrowWords(StateMachine machine, MachineFinding finding, String key, Object... more) {
        Optional<MachineTransition> transition = firstTransition(machine, finding);
        List<Object> arguments = new ArrayList<>();
        arguments.add(transition.map(MachineTransition::condition).orElse(""));
        arguments.add(transition.map(found -> nameOf(machine, found.fromId())).orElse(""));
        arguments.add(transition.map(found -> nameOf(machine, found.toId())).orElse(""));
        arguments.addAll(List.of(more));
        return Messages.format(key, arguments.toArray());
    }

    private static Optional<MachineTransition> firstTransition(StateMachine machine, MachineFinding finding) {
        return finding.transitionIds().stream().findFirst().flatMap(machine::transition);
    }

    private static String stateName(StateMachine machine, MachineFinding finding) {
        return finding.stateIds().stream().findFirst().map(id -> nameOf(machine, id)).orElse("");
    }

    private static String nameOf(StateMachine machine, String stateId) {
        return machine.state(stateId).map(MachineState::name).orElse(stateId);
    }

    private static String firstName(MachineFinding finding) {
        return finding.names().stream().findFirst().orElse("");
    }

    /** «x = 1, y = 0». */
    private static String values(Map<String, Boolean> witness) {
        List<String> parts = new ArrayList<>();
        witness.forEach((name, value) -> parts.add(Messages.format("problem.machine.value", name, value ? 1 : 0)));
        return String.join(LIST_SEPARATOR, parts);
    }
}
