package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.MachinePin;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.machine.StateMachine;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.core.io.CircuitFormatException.invalid;

/**
 * The JSON shape of a «Máquina de estados» (format 7, the {@code machine} of a definition entry): states with their
 * place, Moore outputs and colour, arrows with their condition as typed and their Mealy outputs, the initial state,
 * the pins in order, the bus flags, the edge, the saved tests and the diagram's notes. Empty lists and the defaults
 * of the flags are not written. An output's value is 0 or 1. A pin whose id is its name is written as the name alone
 * (as hitos 1–2 wrote every pin), a renamed one as {@code {"id": "x", "name": "a"}}.
 */
final class MachineEntries {

    private static final Logger LOG = LoggerFactory.getLogger(MachineEntries.class);

    record MachineEntry(List<StateEntry> states,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TransitionEntry> transitions,
                        @JsonInclude(JsonInclude.Include.NON_NULL) String initialState,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<PinEntry> inputs,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<PinEntry> outputs,
                        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean inputsAsBus,
                        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean outputsAsBus,
                        String edge,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TestEntry> tests,
                        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<NoteEntry> notes) {
    }

    /** A state; {@code color} is a lower-case {@link StateColor} name, left out when the state has none. */
    record StateEntry(String id, String name, double x, double y,
                      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<OutputEntry> outputs,
                      @JsonInclude(JsonInclude.Include.NON_NULL) String color) {
    }

    record TransitionEntry(String id, String from, String to, String condition,
                           @JsonInclude(JsonInclude.Include.NON_EMPTY) List<OutputEntry> outputs) {
    }

    record OutputEntry(String output, int value) {
    }

    record TestEntry(String name) {
    }

    /** A note of the diagram; {@code color} is a lower-case {@link StickyColor} name. */
    record NoteEntry(String id, double x, double y, double width, double height, String color, String text) {
    }

    /** A pin: a plain string (its name, also its id) or an object with both when they differ. */
    record PinEntry(String id, String name) {

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        PinEntry(@JsonProperty("id") String id, @JsonProperty("name") String name) {
            this.id = id;
            this.name = name;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static PinEntry named(String name) {
            return new PinEntry(name, name);
        }

        @JsonValue
        Object written() {
            if (id != null && id.equals(name)) {
                return name;
            }
            Map<String, String> both = new LinkedHashMap<>();
            both.put("id", id);
            both.put("name", name);
            return both;
        }
    }

    private MachineEntries() {
    }

    static MachineEntry entry(StateMachine machine) {
        return new MachineEntry(
                machine.states().stream().map(MachineEntries::stateEntry).toList(),
                machine.transitions().stream().map(transition -> new TransitionEntry(transition.id(),
                        transition.fromId(), transition.toId(), transition.condition(),
                        outputEntries(transition.outputs()))).toList(),
                machine.initialStateId().orElse(null), pinEntries(machine.inputs()), pinEntries(machine.outputs()),
                machine.inputsAsBus(), machine.outputsAsBus(), machine.edge(),
                machine.tests().stream().map(test -> new TestEntry(test.name())).toList(),
                machine.notes().stream().map(MachineEntries::noteEntry).toList());
    }

    private static StateEntry stateEntry(MachineState state) {
        return new StateEntry(state.id(), state.name(), state.position().x(), state.position().y(),
                outputEntries(state.outputs()),
                state.color().map(color -> color.name().toLowerCase(Locale.ROOT)).orElse(null));
    }

    private static NoteEntry noteEntry(MachineNote note) {
        return new NoteEntry(note.id(), note.position().x(), note.position().y(), note.width(), note.height(),
                note.color().name().toLowerCase(Locale.ROOT), note.text());
    }

    private static List<PinEntry> pinEntries(List<MachinePin> pins) {
        return pins.stream().map(pin -> new PinEntry(pin.id(), pin.name())).toList();
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
                        outputs(state.outputs(), definitionId), stateColor(state, definitionId)));
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
            List<MachineNote> notes = new ArrayList<>();
            for (NoteEntry note : CircuitFormat.entries(entry.notes())) {
                notes.add(note(CircuitFormat.requireEntry(note, "notes of " + definitionId)));
            }
            String edge = entry.edge() == null ? StateMachine.EDGE_FALLING : entry.edge();
            return new StateMachine(states, transitions, Optional.ofNullable(entry.initialState()),
                    pins(entry.inputs(), "inputs of " + definitionId),
                    pins(entry.outputs(), "outputs of " + definitionId), entry.inputsAsBus(), entry.outputsAsBus(),
                    edge, tests, notes);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid("machine of " + definitionId + ": " + exception.getMessage(), exception);
        }
    }

    private static List<MachinePin> pins(List<PinEntry> entries, String key) throws CircuitFormatException {
        List<MachinePin> pins = new ArrayList<>();
        for (PinEntry entry : CircuitFormat.entries(entries)) {
            CircuitFormat.requireEntry(entry, key);
            pins.add(new MachinePin(entry.id(), entry.name()));
        }
        return pins;
    }

    /**
     * The colour of {@code entry}; an unknown name (a newer VSim's colour, a hand edit) reads as no colour, like an
     * unknown sticky note colour reads as the default, and is logged, so one colour never fails the whole file.
     */
    private static Optional<StateColor> stateColor(StateEntry entry, String definitionId) {
        if (entry.color() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(StateColor.valueOf(entry.color().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            LOG.warn("machine of {}: state {} has an unknown colour {}; read without a colour", definitionId,
                    entry.id(), entry.color());
            return Optional.empty();
        }
    }

    /** A note; an unknown colour reads as yellow, as a circuit's sticky note does. */
    private static MachineNote note(NoteEntry entry) {
        StickyColor color = StickyColor.YELLOW;
        if (entry.color() != null) {
            try {
                color = StickyColor.valueOf(entry.color().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                color = StickyColor.YELLOW;
            }
        }
        return new MachineNote(entry.id(), new DiagramPoint(entry.x(), entry.y()), entry.width(), entry.height(),
                color, entry.text() == null ? "" : entry.text());
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
