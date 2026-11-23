package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.ExpressionParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where a pin name is used in a machine: the states that give it a value (Moore) and the arrows that read it in their
 * condition or give it a value (Mealy). A condition counts by its written names, also when it does not parse, the same
 * way a rename rewrites it ({@link MachineConditions#renamed}). It answers «se usa en 3 flechas» and whether a pin
 * may be removed.
 */
public final class MachinePinUses {

    private MachinePinUses() {
    }

    /** The states and arrows that use a name, in model order. */
    public record Uses(List<String> stateIds, List<String> transitionIds) {

        public Uses {
            stateIds = List.copyOf(stateIds);
            transitionIds = List.copyOf(transitionIds);
        }

        public int count() {
            return stateIds.size() + transitionIds.size();
        }

        public boolean isUnused() {
            return count() == 0;
        }
    }

    /** Where {@code name} is used in {@code machine}. */
    public static Uses of(StateMachine machine, String name) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(name, "name");
        List<String> stateIds = new ArrayList<>();
        for (MachineState state : machine.states()) {
            if (assigns(state.outputs(), name)) {
                stateIds.add(state.id());
            }
        }
        List<String> transitionIds = new ArrayList<>();
        for (MachineTransition transition : machine.transitions()) {
            if (reads(transition.condition(), name) || assigns(transition.outputs(), name)) {
                transitionIds.add(transition.id());
            }
        }
        return new Uses(stateIds, transitionIds);
    }

    private static boolean reads(String condition, String name) {
        return ExpressionParser.nameSpans(condition).stream().anyMatch(span -> span.name().equals(name));
    }

    private static boolean assigns(List<OutputAssignment> assignments, String name) {
        return assignments.stream().anyMatch(assignment -> assignment.output().equals(name));
    }
}
