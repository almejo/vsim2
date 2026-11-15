package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineFinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which states and arrows of a machine's diagram glow for its problems (the mockup's «Los problemas, a la vista»): red
 * for an error, amber for a warning, an error winning when both apply. A problem about arrows makes its arrows glow (two
 * arrows true at once, a bad condition); one about a state makes the state glow (never reached, stays for some
 * input). A problem about neither (no initial state, an unused input) only shows in the lists. Never colour only: the
 * tooltip and the lists say it in words ({@link #findingsOfState}, {@link #findingsOfTransition}).
 */
final class MachineGlows {

    /** No glow at all: a diagram without problems. */
    static final MachineGlows NONE = new MachineGlows(Map.of(), Map.of());

    /** How an element glows. */
    enum Glow {
        ERROR,
        WARNING
    }

    private final Map<String, List<MachineFinding>> byState;
    private final Map<String, List<MachineFinding>> byTransition;

    private MachineGlows(Map<String, List<MachineFinding>> byState,
                         Map<String, List<MachineFinding>> byTransition) {
        this.byState = byState;
        this.byTransition = byTransition;
    }

    /** The glows of {@code findings} (a machine's, in their order). */
    static MachineGlows of(List<MachineFinding> findings) {
        Map<String, List<MachineFinding>> byState = new LinkedHashMap<>();
        Map<String, List<MachineFinding>> byTransition = new LinkedHashMap<>();
        for (MachineFinding finding : findings) {
            statesOf(finding).forEach(id -> byState.computeIfAbsent(id, key -> new ArrayList<>()).add(finding));
            transitionsOf(finding).forEach(id ->
                    byTransition.computeIfAbsent(id, key -> new ArrayList<>()).add(finding));
        }
        return new MachineGlows(byState, byTransition);
    }

    /** The states {@code finding} makes glow. */
    static List<String> statesOf(MachineFinding finding) {
        return glowsOnStates(finding) ? finding.stateIds() : List.of();
    }

    /** The arrows {@code finding} makes glow. */
    static List<String> transitionsOf(MachineFinding finding) {
        return glowsOnStates(finding) ? List.of() : finding.transitionIds();
    }

    /** «Se queda en B» is about the state, though it names B's arrows; any other finding with arrows is about them. */
    private static boolean glowsOnStates(MachineFinding finding) {
        return finding.kind() == MachineFinding.Kind.STAYS_FOR_SOME_INPUT || finding.transitionIds().isEmpty();
    }

    Optional<Glow> ofState(String stateId) {
        return glowOf(findingsOfState(stateId));
    }

    Optional<Glow> ofTransition(String transitionId) {
        return glowOf(findingsOfTransition(transitionId));
    }

    /** The problems that make {@code stateId} glow, in the machine's order. */
    List<MachineFinding> findingsOfState(String stateId) {
        return byState.getOrDefault(stateId, List.of());
    }

    /** The problems that make {@code transitionId} glow, in the machine's order. */
    List<MachineFinding> findingsOfTransition(String transitionId) {
        return byTransition.getOrDefault(transitionId, List.of());
    }

    private static Optional<Glow> glowOf(List<MachineFinding> findings) {
        if (findings.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(findings.stream().allMatch(MachineFinding::isWarning) ? Glow.WARNING : Glow.ERROR);
    }
}
