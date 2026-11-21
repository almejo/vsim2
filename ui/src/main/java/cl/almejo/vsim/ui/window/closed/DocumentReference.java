package cl.almejo.vsim.ui.window.closed;

import cl.almejo.vsim.ui.app.OpenDocument;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The circuit of a closed tab: the open document while some window still holds it (so a component tab of an untitled
 * circuit can come back), then, once it closes ({@link #documentClosed}), only its file and whether it closed with
 * changes, so no closed document is kept in memory. EDT only.
 */
public final class DocumentReference {

    private OpenDocument open;
    private Path path;
    private String displayName;
    private boolean modifiedWhenClosed;

    private DocumentReference(OpenDocument open) {
        this.open = open;
        this.displayName = open.displayName();
    }

    public static DocumentReference of(OpenDocument document) {
        return new DocumentReference(Objects.requireNonNull(document, "document"));
    }

    /** The document while it is still open; empty once it closed. */
    public Optional<OpenDocument> openDocument() {
        return Optional.ofNullable(open);
    }

    /** The circuit's file now (a «Guardar como» while open is followed); empty for one never saved. */
    public Optional<Path> path() {
        return open != null ? open.path() : Optional.ofNullable(path);
    }

    /** The name its tab showed: «contador», «Sin título». */
    public String displayName() {
        return open != null ? open.displayName() : displayName;
    }

    public Closing closing() {
        if (open != null) {
            return Closing.OPEN;
        }
        if (path == null) {
            return Closing.NEVER_SAVED;
        }
        return modifiedWhenClosed ? Closing.LOST_CHANGES : Closing.SAVED;
    }

    /** An untitled circuit nobody touched: nothing to bring back, so it never enters the stack. */
    boolean isUntouchedUntitled() {
        return open != null && open.path().isEmpty() && !open.isModified();
    }

    /** {@code document} closes: when it is this reference's, only its file and how it closed are kept. */
    void documentClosed(OpenDocument document) {
        if (open != document) {
            return;
        }
        path = document.path().orElse(null);
        displayName = document.displayName();
        modifiedWhenClosed = document.isModified();
        open = null;
    }
}
