package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.DocumentWindows;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionDocumentTest {

    private static final WindowState FIRST = new WindowState(0, 0, 960, 1080, false, new ViewState(1.5, 10, -4));
    private static final WindowState SECOND = new WindowState(30, 30, 960, 1080, false, new ViewState(1.5, 10, -4));

    private final List<OpenDocument> opened = new ArrayList<>();

    @AfterEach
    void closeAll() {
        opened.forEach(OpenDocument::close);
    }

    private OpenDocument newDocument() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        opened.add(document);
        return document;
    }

    @Test
    void captureKeepsFileBackedDocumentsWithEveryViewAndLeavesOutUntitledOnes(@TempDir Path directory)
            throws Exception {
        OpenDocument saved = newDocument();
        saved.save(directory.resolve("contador.vsim"));
        OpenDocument untitled = newDocument();
        DocumentWindows<String> windows = new DocumentWindows<>();
        windows.add(saved, "contador 1");
        windows.add(untitled, "sin título");
        windows.add(saved, "contador 2");
        Map<String, WindowState> states = Map.of("contador 1", FIRST, "contador 2", SECOND, "sin título", FIRST);

        assertThat(SessionDocument.capture(windows, states::get)).containsExactly(
                new SessionDocument(directory.resolve("contador.vsim"), List.of(FIRST, SECOND)));
    }
}
