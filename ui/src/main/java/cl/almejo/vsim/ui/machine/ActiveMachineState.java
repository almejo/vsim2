package cl.almejo.vsim.ui.machine;

import java.util.Objects;
import java.util.Optional;

/**
 * What a machine's diagram lights for one copy: the state it is in and, when the simulation knows it, the state and
 * arrow the next clock edge takes with the inputs it has now (spec 2026-11-14 §1 «Seeing what happens»). Ids are the
 * model's ({@code s1}, {@code t3}).
 */
public record ActiveMachineState(String stateId, Optional<String> nextStateId, Optional<String> nextTransitionId) {

    public ActiveMachineState {
        Objects.requireNonNull(stateId, "stateId");
        Objects.requireNonNull(nextStateId, "nextStateId");
        Objects.requireNonNull(nextTransitionId, "nextTransitionId");
    }
}
