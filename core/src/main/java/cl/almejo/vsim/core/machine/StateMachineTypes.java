package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorFactory;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The native type a «Máquina de estados» runs as (spec §2.3): the flattener does not expand its pins-only body but
 * adds one component of this type, with the definition's pins in pinout order (inputs, {@code RST}, {@code CLK},
 * outputs) and a {@link StateMachineBehavior}. The type id is the same for every revision of one machine, so a rebuild
 * after an edit hands the running state to the new behaviour ({@link Behavior#adoptStateFrom}); two types are equal
 * when their machines are, so a rebuild that changes nothing keeps the running instance.
 */
public final class StateMachineTypes {

    public static final String PREFIX = "machine:";

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
     */
    public static ComponentType typeOf(ComponentDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        StateMachine machine = definition.machine().orElseThrow(() ->
                new IllegalArgumentException(definition.id() + " is not a state machine"));
        return new ComponentType(typeIdOf(definition.id()), ComponentCategory.CUSTOM, definition.size(),
                definition.pinout().stream().map(DefinitionPin::spec).toList(), List.of(), new Factory(machine));
    }

    /** Builds the behaviour of one machine; equal for equal machines, so a type keeps its identity across rebuilds. */
    private record Factory(StateMachine machine) implements BehaviorFactory {

        @Override
        public Behavior create(Map<String, Object> parameters) {
            return new StateMachineBehavior(machine);
        }
    }
}
