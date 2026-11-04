package cl.almejo.vsim.core.io.logisim;

import java.util.List;
import java.util.Objects;

/**
 * An instance of the file's circuit {@code circuit}: the subcircuit step turns it into its custom component, so here
 * it only keeps Logisim's {@code facing} (which the circuit's {@code <circ-anchor>} completes) and its label.
 */
public record MappedSubcircuit(String circuit, String facing, String label, List<ImportNote> notes)
        implements LogisimMapping {

    public MappedSubcircuit {
        Objects.requireNonNull(circuit, "circuit");
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(label, "label");
        notes = List.copyOf(notes);
    }
}
