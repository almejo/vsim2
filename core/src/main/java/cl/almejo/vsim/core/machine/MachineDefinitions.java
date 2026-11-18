package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;

import java.util.List;
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

    /**
     * {@code definition} without its machine's tests, same revision, body and pinout (tests change neither): what
     * «Mis componentes» keeps, because tests live only in the circuit file (the user's rule, 2026-11-17: they would be
     * copied everywhere). {@code definition} itself when it has no tests or is no machine.
     */
    public static ComponentDefinition withoutTests(ComponentDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definition.machine().map(machine -> machine.tests().isEmpty()).orElse(true)) {
            return definition;
        }
        return withTests(definition, List.of());
    }

    /** {@code bundle} with every machine in it {@link #withoutTests without its tests}: what a library file holds. */
    public static DefinitionBundle forLibrary(DefinitionBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        return new DefinitionBundle(withoutTests(bundle.root()),
                bundle.dependencies().stream().map(MachineDefinitions::withoutTests).toList());
    }

    /**
     * {@code incoming}, the library's copy of a machine the circuit follows («Actualizar desde la librería»), with the
     * tests of the circuit's own copy {@code current}: the library has none, and the circuit's are its own. They are
     * {@link MachineTests#fittedTo fitted} to the incoming pins by id (a renamed or moved pin keeps its values, a
     * removed one's are dropped). {@code incoming} itself when the circuit's copy has no tests or either is no machine.
     */
    public static ComponentDefinition keepingTests(ComponentDefinition current, ComponentDefinition incoming) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(incoming, "incoming");
        if (current.machine().isEmpty() || incoming.machine().isEmpty()
                || current.machine().get().tests().isEmpty()) {
            return incoming;
        }
        StateMachine machine = incoming.machine().get();
        return withTests(incoming, MachineTests.fittedTo(machine, current.machine().get().tests()));
    }

    /** {@code definition}, a machine, with {@code tests}: nothing else changes, not even the revision. */
    private static ComponentDefinition withTests(ComponentDefinition definition, List<MachineTest> tests) {
        return new ComponentDefinition(definition.id(), definition.name(), definition.description(),
                definition.size(), definition.pinout(), definition.body(), definition.appearance(),
                definition.revision(), Optional.of(definition.machine().orElseThrow().withTests(tests)));
    }

    private static void requireMachine(ComponentDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (!definition.isMachine()) {
            throw new IllegalArgumentException(definition.id() + " is not a state machine");
        }
    }
}
