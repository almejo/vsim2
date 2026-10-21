package cl.almejo.vsim.automation;

/**
 * The version of one open circuit an edit is prepared against (spec §8, A§6.1): it grows with every change of that
 * circuit's history, also undo and redo, so an edit prepared against an older version is stale. Only meaningful
 * together with its {@link DocumentId}, which carries the identity of the circuit.
 */
public record DocumentVersion(long value) {

    public DocumentVersion {
        if (value < 0) {
            throw new IllegalArgumentException("a document version is never negative: " + value);
        }
    }
}
