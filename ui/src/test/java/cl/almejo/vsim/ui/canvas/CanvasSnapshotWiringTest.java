package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canvas hands every new displayed snapshot to the window's listeners. Review M4: nothing else feeds the Problems
 * controller in the app, so this pins the registration (the other tests call {@code showSnapshot} themselves).
 */
class CanvasSnapshotWiringTest {

    private static final Path SHORT_CIRCUIT = Path.of("../core/src/test/resources/fixtures/m3/short-circuit.vsim");

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void open() throws Exception {
        openDocument = OpenDocument.open(SHORT_CIRCUIT, StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
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

    /** Review M5: like the Problems tab, the status bar warning ignores a snapshot of an older netlist. */
    @Test
    void theStatusBarCountsShortCircuitsOnlyFromACurrentSnapshot() {
        waitUntil(() -> openDocument.latestSnapshot().running()
                && openDocument.latestSnapshot().token() == openDocument.netlistToken()
                && !openDocument.latestSnapshot().shortCircuits().isEmpty());
        SimulationSnapshot current = openDocument.latestSnapshot();
        SimulationSnapshot stale = new SimulationSnapshot(current.revision(), current.token() - 1, current.time(),
                current.running(), current.netValues(), current.componentStates(), current.problems(),
                current.failure());

        view.canvas().display(stale);
        assertThat(view.statusBar().text()).doesNotContain("⚠");

        view.canvas().display(current);
        assertThat(view.statusBar().text()).contains("⚠ " + current.shortCircuits().size());

        view.canvas().display(stale);
        assertThat(view.statusBar().text()).contains("⚠ " + current.shortCircuits().size());
    }
}
