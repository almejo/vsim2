package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.ui.app.DaemonThreads;
import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Archivo ▸ Exportar ▸ «Circuito de Logisim (.circ) (beta)…» in every editor view, after the other exports
 * ({@link LogisimExportFeatures}).
 */
public final class LogisimExportExtension implements EditorExtension {

    /** The file extension of Logisim's projects. */
    static final String EXTENSION = ".circ";

    /** Asks the user where to write; empty when cancelled. An existing file is replaced only once confirmed. */
    @FunctionalInterface
    interface Chooser {
        Optional<Path> choose(Component parent, Path directory, String suggestedName);
    }

    private final Chooser chooser;
    private final LogisimImportDialogs dialogs;
    private final Executor worker;
    private final Executor eventThread;

    /** The real thing: a save dialog, the import's dialogs, a worker thread of its own. */
    public LogisimExportExtension() {
        this(LogisimExportExtension::chooseFile, new SwingLogisimImportDialogs(),
                DaemonThreads.single("logisim-export"), SwingUtilities::invokeLater);
    }

    /** {@code worker} converts and writes, {@code eventThread} tells the user. */
    LogisimExportExtension(Chooser chooser, LogisimImportDialogs dialogs, Executor worker, Executor eventThread) {
        this.chooser = chooser;
        this.dialogs = dialogs;
        this.worker = worker;
        this.eventThread = eventThread;
    }

    @Override
    public Runnable install(EditorView view) {
        LogisimExportFeatures features = new LogisimExportFeatures(view, chooser, dialogs, worker, eventThread);
        return features::dispose;
    }

    private static Optional<Path> chooseFile(Component parent, Path directory, String suggestedName) {
        return FileDialogs.chooseSave(parent, directory, suggestedName,
                new FileNameExtensionFilter(Messages.text(LogisimExportTexts.CHOOSE_FILTER), "circ"),
                path -> FileNames.withExtension(path, EXTENSION));
    }
}
