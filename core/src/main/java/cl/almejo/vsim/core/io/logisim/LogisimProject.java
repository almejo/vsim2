package cl.almejo.vsim.core.io.logisim;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A whole Logisim file as {@link LogisimReader} reads it: the main circuit's name, the project options
 * ({@code gateUndefined}, {@code simlimit}, …), every circuit in file order and what the reader could not take.
 */
public record LogisimProject(String mainCircuit, Map<String, String> options, List<LogisimCircuit> circuits,
                             List<ImportNote> notes) {

    public LogisimProject {
        Objects.requireNonNull(mainCircuit, "mainCircuit");
        options = Map.copyOf(options);
        circuits = List.copyOf(circuits);
        notes = List.copyOf(notes);
    }

    /** The circuit named {@code name}; Logisim names are case-sensitive. */
    public Optional<LogisimCircuit> circuit(String name) {
        return circuits.stream().filter(circuit -> circuit.name().equals(name)).findFirst();
    }
}
