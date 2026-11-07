package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.standard.BitGroups;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The problem the «Grupos» window shows for a bus splitter's or joiner's {@code groups}, told to the AI in words (core
 * gives a bundle key and its arguments): «bit 40 does not exist on a bus of 8 bits», with the part's own width.
 */
final class BusGroupsProblems {

    private BusGroupsProblems() {
    }

    /**
     * Refuses {@code rawParameters} for part {@code partName} of {@code type} when its groups have a problem; any other
     * part, or valid groups, passes.
     */
    static void require(ComponentType type, Map<String, Object> rawParameters, String partName) {
        if (!StandardComponentTypes.hasBitGroups(type.id())) {
            return;
        }
        Optional<ParameterProblem> problem = type.check(rawParameters);
        if (problem.isPresent() && problem.get().parameterName().equals(StandardComponentTypes.GROUPS)) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "The groups \""
                    + rawParameters.get(StandardComponentTypes.GROUPS) + "\" of " + partName + " are not valid: "
                    + describe(problem.get().messageKey(), problem.get().arguments()) + " (nothing was changed)");
        }
    }

    /** What is wrong, in English: the same problems, in the same order, as the window lists. */
    static String describe(String messageKey, List<Object> arguments) {
        return switch (messageKey) {
            case BitGroups.BIT_OUT_OF_RANGE -> "bit " + arguments.get(0) + " does not exist on a bus of "
                    + arguments.get(1) + " bits (its bits are 0 to " + ((Integer) arguments.get(1) - 1) + ")";
            case BitGroups.BIT_IN_TWO_PINS -> "bit " + arguments.get(0) + " is in two pins";
            case BitGroups.EMPTY_PIN -> "pin " + arguments.get(0) + " (counting from 1) has no bits";
            case BitGroups.TOO_MANY_PINS -> "there can be at most " + arguments.get(0) + " pins";
            case BitGroups.INVALID_TEXT -> "the text cannot be read: write ranges like 0-3,7 and separate the pins "
                    + "with |";
            default -> messageKey + " " + arguments;
        };
    }
}
