package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.app.DocumentWindows;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** One circuit of the last session: its file and the state of each of its views, in opening order. */
public record SessionDocument(Path path, List<WindowState> windows) {

    public SessionDocument {
        Objects.requireNonNull(path, "path");
        windows = List.copyOf(windows);
    }

    /**
     * The file-backed documents shown in {@code windows}, in opening order, each with the state of every view;
     * untitled documents are left out (Assumption 21: they come back only through recovery).
     */
    public static <W> List<SessionDocument> capture(DocumentWindows<W> windows, Function<W, WindowState> stateOf) {
        List<SessionDocument> documents = new ArrayList<>();
        for (OpenDocument document : windows.documents()) {
            document.path().ifPresent(path -> documents.add(
                    new SessionDocument(path, windows.windowsOf(document).stream().map(stateOf).toList())));
        }
        return documents;
    }
}
