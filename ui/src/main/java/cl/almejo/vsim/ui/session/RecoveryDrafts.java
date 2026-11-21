package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.OpenDocument;

/**
 * Work the editor keeps outside a document, in memory only, that the document's recovery copy carries too (fix round 2
 * of hito 4: a machine's test draft). Autosave copies a document while it is modified <em>or</em> holds such work, and
 * every edit of the work counts as a change. EDT only.
 */
public interface RecoveryDrafts {

    /** No work outside the documents: a copy is the document itself. */
    RecoveryDrafts NONE = new RecoveryDrafts() {
        @Override
        public boolean holds(OpenDocument document) {
            return false;
        }

        @Override
        public long changeCount(OpenDocument document) {
            return 0;
        }

        @Override
        public String serialized(OpenDocument document) {
            return document.serialized();
        }
    };

    /** Whether the editor holds work of {@code document} that the document itself does not have. */
    boolean holds(OpenDocument document);

    /** A number that changes with every edit of that work (together with the document's own change count). */
    long changeCount(OpenDocument document);

    /** The content of {@code document}'s recovery copy: the document with that work in it. */
    String serialized(OpenDocument document);
}
