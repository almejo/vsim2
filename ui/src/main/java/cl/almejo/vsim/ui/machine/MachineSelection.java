package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.Listeners;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What is selected in a machine's diagram: states and arrows by their model ids. The diagram, its gestures and the
 * right panel share one per tab; the listeners hear every real change. EDT only.
 */
public final class MachineSelection {

    private Set<String> stateIds = Set.of();
    private Set<String> transitionIds = Set.of();
    private final Listeners<Runnable> listeners = new Listeners<>();

    /** The selected states' ids, in selection order (read-only). */
    public Set<String> stateIds() {
        return stateIds;
    }

    /** The selected arrows' ids, in selection order (read-only). */
    public Set<String> transitionIds() {
        return transitionIds;
    }

    public boolean containsState(String stateId) {
        return stateIds.contains(stateId);
    }

    public boolean containsTransition(String transitionId) {
        return transitionIds.contains(transitionId);
    }

    public boolean isEmpty() {
        return stateIds.isEmpty() && transitionIds.isEmpty();
    }

    /** Selects exactly {@code states} and {@code transitions}. */
    public void set(Set<String> states, Set<String> transitions) {
        Set<String> newStates = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(states)));
        Set<String> newTransitions = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(transitions)));
        if (newStates.equals(stateIds) && newTransitions.equals(transitionIds)) {
            return;
        }
        stateIds = newStates;
        transitionIds = newTransitions;
        listeners.fire(Runnable::run);
    }

    /** Selects the state {@code stateId} alone (a click on it). */
    public void selectState(String stateId) {
        set(Set.of(Objects.requireNonNull(stateId, "stateId")), Set.of());
    }

    /** Selects the arrow {@code transitionId} alone (a click on it). */
    public void selectTransition(String transitionId) {
        set(Set.of(), Set.of(Objects.requireNonNull(transitionId, "transitionId")));
    }

    public void clear() {
        set(Set.of(), Set.of());
    }

    /** Forgets what {@code machine} no longer has (a deleted state, an undone arrow). */
    public void retainExisting(StateMachine machine) {
        Set<String> existingStates = machine.states().stream().map(MachineState::id).collect(Collectors.toSet());
        Set<String> existingTransitions = machine.transitions().stream().map(MachineTransition::id)
                .collect(Collectors.toSet());
        set(stateIds.stream().filter(existingStates::contains).collect(Collectors.toCollection(LinkedHashSet::new)),
                transitionIds.stream().filter(existingTransitions::contains)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /** Runs {@code listener} after every change; returns its removal. */
    public Runnable addListener(Runnable listener) {
        return listeners.add(Objects.requireNonNull(listener, "listener"));
    }
}
