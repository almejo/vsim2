package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.simulation.OutdatedPieceChecks;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.problems.ProblemsController;

import javax.swing.SwingUtilities;

/**
 * Keeps the Problems tab's lines about pieces that use an older version of a component than the library's, when
 * updating them would break inner connections. Recomputed when the circuit or the library changes (and once when
 * installed, which is when a file is opened); nothing is updated automatically. The document's registry reads the
 * document, so the work always runs on the event thread, even when the library reports from its watcher.
 */
final class OutdatedPiecesReporter {

    private final OpenDocument openDocument;
    private final ComponentLibrary library;
    private final ProblemsController problems;
    private final Runnable listener = this::refreshOnTheEventThread;
    private boolean disposed;

    OutdatedPiecesReporter(OpenDocument openDocument, ComponentLibrary library, ProblemsController problems) {
        this.openDocument = openDocument;
        this.library = library;
        this.problems = problems;
    }

    void install() {
        library.addListener(listener);
        openDocument.addChangeListener(listener);
        refresh();
    }

    void dispose() {
        disposed = true;
        library.removeListener(listener);
        openDocument.removeChangeListener(listener);
    }

    private void refreshOnTheEventThread() {
        if (SwingUtilities.isEventDispatchThread()) {
            refresh();
        } else {
            SwingUtilities.invokeLater(this::refresh);
        }
    }

    private void refresh() {
        if (!disposed) {
            problems.showOutdatedPieces(OutdatedPieceChecks.problems(openDocument.document(), library,
                    openDocument.registry().copy()));
        }
    }
}
