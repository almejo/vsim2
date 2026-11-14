package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.library.ComponentDefinition;

import java.util.Objects;
import java.util.Optional;

/** Makes and updates the definitions of «Máquina de estados» user components (spec §2.2). */
public final class MachineDefinitions {

    private MachineDefinitions() {
    }

    /** A new machine {@code id} called {@code name}: one initial state {@code S0}, only {@code RST} and {@code CLK}. */
    public static ComponentDefinition create(String id, String name) {
        StateMachine machine = StateMachine.initial();
        MachineBody generated = MachineBody.of(machine);
        return new ComponentDefinition(id, name, "", generated.size(), generated.pinout(), generated.body(),
                Optional.empty(), 1, Optional.of(machine));
    }

    /**
     * {@code previous} with {@code newMachine} after an edit: the names now in use become pins
     * ({@link StateMachine#withUsedNamesAdded()}), the body is generated again and the revision advances when
     * anything changed ({@code previous} itself when nothing did).
     *
     * @throws IllegalArgumentException when {@code previous} is not a machine
     */
    public static ComponentDefinition edited(ComponentDefinition previous, StateMachine newMachine) {
        requireMachine(previous);
        return previous.withMachine(newMachine.withUsedNamesAdded()).revisedFrom(previous);
    }

    /**
     * {@code definition} with its body, pinout and size generated from its machine, same revision: what a file or the
     * clipboard says about the body is only a copy, and the model wins when they disagree. {@code definition} itself
     * when it already agrees.
     */
    public static ComponentDefinition regenerated(ComponentDefinition definition) {
        requireMachine(definition);
        ComponentDefinition generated = definition.withMachine(definition.machine().orElseThrow());
        return generated.equals(definition) ? definition : generated;
    }

    private static void requireMachine(ComponentDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (!definition.isMachine()) {
            throw new IllegalArgumentException(definition.id() + " is not a state machine");
        }
    }
}
