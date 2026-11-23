package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorFactory;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The native type a «Máquina de estados» runs as (spec §2.3): the flattener does not expand its pins-only body but
 * adds one component of this type, with the definition's pins in pinout order (inputs, {@code RST}, {@code CLK},
 * outputs, each side maybe one bus) and a {@link StateMachineBehavior}, which finds them by name
 * ({@link MachinePinLayout}). The type id is the same for every revision of one machine, so a rebuild
 * after an edit hands the running state to the new behaviour ({@link Behavior#adoptStateFrom}); two types are equal
 * when their machines behave alike ({@link #behaviourOf}: tests, notes, colours and places do not count), so a
 * rebuild that changes no behaviour keeps the running instance.
 * <p>
 * After the pinout the type has one hidden output, the state channel: it carries the current state's code, its index
 * in {@link StateMachine#states()}, {@link #stateWidth} bits wide, or Z while the machine holds no state. The
 * document's type of the piece does not have it, so it is never drawn nor wired; the flattener gives it a net of its
 * own, {@link #stateNetId}, which the waveform recorder watches like any net.
 */
public final class StateMachineTypes {

    public static final String PREFIX = "machine:";

    /** The hidden state pin's name, also the suffix of its net: no pin name the user gives holds {@code #}. */
    static final String STATE_PIN_NAME = "#state";

    private StateMachineTypes() {
    }

    public static String typeIdOf(String definitionId) {
        return PREFIX + definitionId;
    }

    /**
     * The native type of the machine {@code definition}; callers that flatten many pieces cache it per definition
     * object (the flattener does, per flatten).
     *
     * @throws IllegalArgumentException when {@code definition} is not a machine
     * @throws IllegalStateException    when its pinout disagrees with its machine ({@link MachinePinLayout})
     */
    public static ComponentType typeOf(ComponentDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        StateMachine machine = definition.machine().orElseThrow(() ->
                new IllegalArgumentException(definition.id() + " is not a state machine"));
        List<PinSpec> pins = new ArrayList<>(definition.pinout().stream().map(DefinitionPin::spec).toList());
        pins.add(PinSpec.output(STATE_PIN_NAME, 0, 0, stateWidth(machine)));
        String typeId = typeIdOf(definition.id());
        ComponentType pinsOnly = new ComponentType(typeId, ComponentCategory.CUSTOM, definition.size(), pins,
                List.of(), parameters -> {
                    throw new IllegalStateException(typeId + " is being built");
                });
        MachinePinLayout layout = MachinePinLayout.of(pinsOnly, machine);
        return new ComponentType(typeId, ComponentCategory.CUSTOM, definition.size(), pins, List.of(),
                new Factory(machine, layout));
    }

    /** The machine a native type runs, when {@code type} is one made by {@link #typeOf}. */
    public static Optional<StateMachine> machineOf(ComponentType type) {
        Objects.requireNonNull(type, "type");
        return type.behaviorFactory() instanceof Factory factory ? Optional.of(factory.machine()) : Optional.empty();
    }

    /**
     * How many bits the state code needs: enough for every index of {@code machine}'s states, at least one (one state,
     * or none, takes one bit). Four states take 2 bits, five take 3.
     */
    public static int stateWidth(StateMachine machine) {
        int states = Math.max(1, machine.states().size());
        return Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(states - 1));
    }

    /**
     * The index of the hidden state pin in {@code type}, a native machine type made by {@link #typeOf}: the pin its
     * behaviour drives, found by name, so the flattener and the behaviour never disagree.
     *
     * @throws IllegalArgumentException when {@code type} has no state pin
     */
    public static int statePinIndex(ComponentType type) {
        return type.pinIndexOf(STATE_PIN_NAME).orElseThrow(() ->
                new IllegalArgumentException(type.id() + " has no state pin"));
    }

    /** The net the flattener gives the state pin of the machine copy {@code flatComponentId} ({@code w/m}). */
    public static String stateNetId(String flatComponentId) {
        return flatComponentId + STATE_PIN_NAME;
    }

    /** The code of state {@code stateId}: its index in {@code machine}'s states; empty for an unknown state. */
    public static OptionalInt stateCode(StateMachine machine, String stateId) {
        List<MachineState> states = machine.states();
        for (int index = 0; index < states.size(); index++) {
            if (states.get(index).id().equals(stateId)) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /** The state whose code is {@code code}; empty when no state has it (a code recorded before an edit). */
    public static Optional<String> stateIdOfCode(StateMachine machine, long code) {
        if (code < 0 || code >= machine.states().size()) {
            return Optional.empty();
        }
        return Optional.of(machine.states().get((int) code).id());
    }

    /**
     * The part of {@code machine} its behaviour runs on: states with their names and outputs, arrows, the initial
     * state, the pins, the bus flags and the edge; without the states' places and colours, the notes, the free texts or the tests,
     * which only the editor reads. A state's name stays: the running copy shows it (the face, the probe).
     */
    static StateMachine behaviourOf(StateMachine machine) {
        return machine.withStates(machine.states().stream()
                        .map(state -> new MachineState(state.id(), state.name(), DiagramPoint.ORIGIN, state.outputs()))
                        .toList())
                .withNotes(List.of())
                .withTexts(List.of())
                .withTests(List.of());
    }

    /**
     * Builds the behaviour of one machine, which finds its pins through {@code layout} (the state code goes on its
     * hidden pin). Equal for machines of equal {@linkplain #behaviourOf behaviour} and pinouts, so a type keeps its
     * identity across rebuilds, and an edit of a test, a note, a colour or a place keeps the running copy (hito 4,
     * task 2) instead of building it again.
     */
    private static final class Factory implements BehaviorFactory {

        private final StateMachine machine;
        private final StateMachine behaviour;
        private final MachinePinLayout layout;

        Factory(StateMachine machine, MachinePinLayout layout) {
            this.machine = machine;
            this.behaviour = behaviourOf(machine);
            this.layout = layout;
        }

        StateMachine machine() {
            return machine;
        }

        @Override
        public Behavior create(Map<String, Object> parameters) {
            return new StateMachineBehavior(machine, layout);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Factory factory && behaviour.equals(factory.behaviour)
                    && layout.equals(factory.layout);
        }

        @Override
        public int hashCode() {
            return Objects.hash(behaviour, layout);
        }
    }
}
