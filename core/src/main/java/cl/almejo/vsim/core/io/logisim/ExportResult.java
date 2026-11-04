package cl.almejo.vsim.core.io.logisim;

import java.util.List;
import java.util.Objects;

/**
 * A VSim document as a Logisim project ({@link LogisimExporter}), ready for {@link LogisimWriter}, and what the export
 * could not take as it is: parts Logisim has no equivalent for, options it lacks ({@link LogisimNotes}' export keys).
 */
public record ExportResult(LogisimProject project, List<ImportNote> notes) {

    public ExportResult {
        Objects.requireNonNull(project, "project");
        notes = List.copyOf(notes);
    }
}
