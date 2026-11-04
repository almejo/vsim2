package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.document.CircuitDocument;

import java.util.List;
import java.util.Objects;

/**
 * A Logisim project imported by {@link LogisimImporter}: the main circuit as a document holding a custom component
 * definition for each of the project's other circuits, and every note of the way (sorted by circuit, then kind).
 */
public record ImportResult(CircuitDocument document, List<ImportNote> notes) {

    public ImportResult {
        Objects.requireNonNull(document, "document");
        notes = List.copyOf(notes);
    }
}
