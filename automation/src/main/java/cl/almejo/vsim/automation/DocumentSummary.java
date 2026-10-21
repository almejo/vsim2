package cl.almejo.vsim.automation;

import java.util.Objects;

/** One open circuit as {@code vsim_list_documents} shows it: id, the name its window shows, version, modified. */
public record DocumentSummary(DocumentId id, String name, DocumentVersion version, boolean modified) {

    public DocumentSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
    }
}
