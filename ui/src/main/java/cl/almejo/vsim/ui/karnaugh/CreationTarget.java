package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.DefinitionBundle;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where a circuit built from a table goes (spec §3 step 5, §6): a new circuit window, or floating over the circuit the
 * wizard or the import came from; and the component library for «Crear también como componente». The application's is
 * {@link ViewCreationTarget}; tests use a fake. Every method runs on the EDT.
 */
public interface CreationTarget {

    /** Opens a new circuit window named {@code name} whose first undo step adds {@code fragment}. */
    void openNewCircuit(String name, CircuitFragment fragment);

    /** Whether there is a circuit to insert into («Insertar en el circuito actual» is off otherwise). */
    boolean canInsert();

    /** Floats {@code fragment} over the current circuit, like a paste: the user clicks to drop it (one undo step). */
    void insertFloating(CircuitFragment fragment);

    /** A short notice in the current circuit window's status bar. */
    void notice(String text);

    /** The component names the library and the current circuit already use: a new component must not repeat them. */
    List<String> takenComponentNames();

    /** The definition ids the library and the current circuit already use. */
    Set<String> takenComponentIds();

    /** Writes {@code bundle} to the component library; empty when it was written, or the reason it was not. */
    Optional<String> saveComponent(DefinitionBundle bundle);
}
