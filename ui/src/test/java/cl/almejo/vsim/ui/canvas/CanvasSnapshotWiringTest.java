package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canvas hands every new displayed snapshot to the window's listeners. Review M4: nothing else feeds the Problems
 * controller in the app, so this pins the registration (the other tests call {@code showSnapshot} themselves).
 */
class CanvasSnapshotWiringTest {

    private static final Path SHORT_CIRCUIT = Path.of("../examples/m3/short-circuit.vsim");

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void open() throws Exception {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.open(SHORT_CIRCUIT, StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }
        });
        view.canvas().setSize(800, 600);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    @Test
    void aRefreshedSnapshotReachesTheProblemsController() {
        waitUntil(() -> openDocument.latestSnapshot().running()
                && openDocument.latestSnapshot().token() == openDocument.netlistToken()
                && !openDocument.latestSnapshot().shortCircuits().isEmpty());
        assertThat(view.problems().navigator().problems()).isEmpty();
        assertThat(view.problems().nextProblem().isEnabled()).isFalse();

        view.canvas().refreshSnapshot();

        assertThat(view.problems().navigator().problems()).isNotEmpty();
        assertThat(view.problems().nextProblem().isEnabled()).isTrue();
    }
}
