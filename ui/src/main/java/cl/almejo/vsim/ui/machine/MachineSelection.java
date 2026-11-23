package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineText;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.Listeners;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What is selected in a machine's diagram: states, arrows, notes and free texts by their model ids. The diagram, its gestures and
 * the right panel share one per tab; the listeners hear every real change. EDT only.
 */
public final class MachineSelection {

    private Set<String> stateIds = Set.of();
    private Set<String> transitionIds = Set.of();
    private Set<String> noteIds = Set.of();
    private Set<String> textIds = Set.of();
    private final Listeners<Runnable> listeners = new Listeners<>();

    /** The selected states' ids, in selection order (read-only). */
    public Set<String> stateIds() {
        return stateIds;
    }

    /** The selected arrows' ids, in selection order (read-only). */
    public Set<String> transitionIds() {
        return transitionIds;
    }

    /** The selected notes' ids, in selection order (read-only). */
    public Set<String> noteIds() {
        return noteIds;
    }

    /** The selected free texts' ids, in selection order (read-only). */
    public Set<String> textIds() {
        return textIds;
    }

    public boolean containsState(String stateId) {
        return stateIds.contains(stateId);
    }

    public boolean containsTransition(String transitionId) {
        return transitionIds.contains(transitionId);
    }

    public boolean containsNote(String noteId) {
        return noteIds.contains(noteId);
    }

    public boolean containsText(String textId) {
        return textIds.contains(textId);
    }

    public boolean isEmpty() {
        return stateIds.isEmpty() && transitionIds.isEmpty() && noteIds.isEmpty() && textIds.isEmpty();
    }

    /** How many things are selected, of every kind. */
    public int count() {
        return stateIds.size() + transitionIds.size() + noteIds.size() + textIds.size();
    }

    /** Selects exactly {@code states} and {@code transitions}, and no note. */
    public void set(Set<String> states, Set<String> transitions) {
        set(states, transitions, Set.of());
    }

    /** Selects exactly {@code states}, {@code transitions} and {@code notes}, and no free text. */
    public void set(Set<String> states, Set<String> transitions, Set<String> notes) {
        set(states, transitions, notes, Set.of());
    }

    /** Selects exactly {@code states}, {@code transitions}, {@code notes} and {@code texts}. */
    public void set(Set<String> states, Set<String> transitions, Set<String> notes, Set<String> texts) {
        Set<String> newStates = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(states)));
        Set<String> newTransitions = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(transitions)));
        Set<String> newNotes = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(notes)));
        Set<String> newTexts = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(texts)));
        if (newStates.equals(stateIds) && newTransitions.equals(transitionIds) && newNotes.equals(noteIds)
                && newTexts.equals(textIds)) {
            return;
        }
        stateIds = newStates;
        transitionIds = newTransitions;
        noteIds = newNotes;
        textIds = newTexts;
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

    /** Selects the note {@code noteId} alone (a click on it). */
    public void selectNote(String noteId) {
        set(Set.of(), Set.of(), Set.of(Objects.requireNonNull(noteId, "noteId")));
    }

    /** Selects the free text {@code textId} alone (a click on it). */
    public void selectText(String textId) {
        set(Set.of(), Set.of(), Set.of(), Set.of(Objects.requireNonNull(textId, "textId")));
    }

    public void clear() {
        set(Set.of(), Set.of(), Set.of(), Set.of());
    }

    /** Forgets what {@code machine} no longer has (a deleted state, an undone arrow or note). */
    public void retainExisting(StateMachine machine) {
        Set<String> existingStates = machine.states().stream().map(MachineState::id).collect(Collectors.toSet());
        Set<String> existingTransitions = machine.transitions().stream().map(MachineTransition::id)
                .collect(Collectors.toSet());
        Set<String> existingNotes = machine.notes().stream().map(MachineNote::id).collect(Collectors.toSet());
        Set<String> existingTexts = machine.texts().stream().map(MachineText::id).collect(Collectors.toSet());
        set(stateIds.stream().filter(existingStates::contains).collect(Collectors.toCollection(LinkedHashSet::new)),
                transitionIds.stream().filter(existingTransitions::contains)
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                noteIds.stream().filter(existingNotes::contains).collect(Collectors.toCollection(LinkedHashSet::new)),
                textIds.stream().filter(existingTexts::contains).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /** Runs {@code listener} after every change; returns its removal. */
    public Runnable addListener(Runnable listener) {
        return listeners.add(Objects.requireNonNull(listener, "listener"));
    }
}
