package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.core.truthtable.Preparation;

import java.util.Objects;

/**
 * What the editor prepared on its thread for a {@link TruthTableRequest}: the scope as an immutable copy (or why there
 * is no table) and the circuit version it was taken from. The rows are then evaluated off the editor thread.
 */
public record PreparedTruthTable(DocumentVersion version, Preparation preparation) {

    public PreparedTruthTable {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(preparation, "preparation");
    }
}
