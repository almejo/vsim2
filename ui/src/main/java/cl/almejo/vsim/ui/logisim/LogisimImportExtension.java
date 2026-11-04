package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.ui.app.DaemonThreads;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.logisim.ImportResult;
import cl.almejo.vsim.core.io.logisim.LogisimImporter;
import cl.almejo.vsim.ui.app.FileDialogs;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.ImportMenu;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Archivo ▸ Importar ▸ «Circuito de Logisim (.circ) (beta)…»: asks for a file, reads and converts it off the event thread
 * ({@link LogisimImportJob}, with a progress dialog only when it is slow) and opens the result as a new unsaved
 * document named after the file. When the import left notes, the report lists them after the document opens; a file
 * that is not a Logisim project gets a dialog with its own reason.
 */
public final class LogisimImportExtension implements EditorExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogisimImportExtension.class);
    /** The standard parts only: the file's subcircuits become definitions inside the imported document. */
    private static final ComponentTypeRegistry BASE_REGISTRY = StandardComponentTypes.registry();

    /** Asks the user for the file; empty when cancelled. */
    @FunctionalInterface
    interface Chooser {
        Optional<Path> choose(Component parent, Path directory);
    }

    private final Supplier<Path> startDirectory;
    private final Consumer<Path> chosen;
    private final Chooser chooser;
    private final Function<Component, Optional<WindowCommands>> windowOf;
    private final LogisimImportDialogs dialogs;
    private final Executor worker;
    private final Executor eventThread;
    private final Delayer delayer;

    /** The real thing; the chooser starts in {@code startDirectory} and tells {@code chosen} which file it took. */
    public LogisimImportExtension(Supplier<Path> startDirectory, Consumer<Path> chosen) {
        this(startDirectory, chosen, LogisimImportExtension::chooseFile, LogisimImportExtension::windowCommandsOf,
                new SwingLogisimImportDialogs(), DaemonThreads.single("logisim-import"),
                SwingUtilities::invokeLater, Delayer.swing());
    }

    LogisimImportExtension(Supplier<Path> startDirectory, Consumer<Path> chosen, Chooser chooser,
                           Function<Component, Optional<WindowCommands>> windowOf, LogisimImportDialogs dialogs,
                           Executor worker, Executor eventThread, Delayer delayer) {
        this.startDirectory = startDirectory;
        this.chosen = chosen;
        this.chooser = chooser;
        this.windowOf = windowOf;
        this.dialogs = dialogs;
        this.worker = worker;
        this.eventThread = eventThread;
        this.delayer = delayer;
    }

    @Override
    public Runnable install(EditorView view) {
        JMenuItem item = new JMenuItem(Messages.text(LogisimImportTexts.MENU_ITEM));
        item.addActionListener(event -> chooser.choose(view, startDirectory.get()).ifPresent(file -> {
            chosen.accept(file);
            importFile(file, view);
        }));
        return ImportMenu.add(view.menu(MenuId.FILE), item);
    }

    private void importFile(Path file, EditorView view) {
        LogisimImportJob.Outcome outcome = new LogisimImportJob.Outcome() {
            @Override
            public void imported(Path imported, ImportResult result) {
                open(imported, result, view);
            }

            @Override
            public void failed(Path failed, Exception problem) {
                tell(failed, problem, view);
            }
        };
        new LogisimImportJob(worker, eventThread, delayer, LogisimImportExtension::load, dialogs.progress(view),
                outcome).start(file);
    }

    private void open(Path file, ImportResult result, EditorView view) {
        String name = documentName(file);
        // The circuit keeps Logisim's main circuit name (the notes name it so too); the window shows the file's name.
        String mainCircuit = result.document().name();
        Optional<EditorView> shown = windowOf.apply(view)
                .flatMap(window -> window.showUntitled(result.document(), name));
        // The report names and goes to the parts of the window that shows the new document.
        ReportHost host = new EditorReportHost(shown, shown.orElse(view).services().preferences());
        ImportReport report = ImportReport.of(result.notes(), host, mainCircuit);
        if (!report.isEmpty()) {
            // The requester window may be gone (a pristine one is replaced by the new document): its own parent.
            dialogs.report(shown.orElse(null), Messages.text(LogisimImportTexts.REPORT_TITLE),
                    Messages.format(LogisimImportTexts.REPORT_SUMMARY, name, report.noteCount()), report, host);
        }
    }

    private void tell(Path file, Exception problem, EditorView view) {
        LOGGER.warn("Could not import {}", file, problem);
        String name = String.valueOf(file.getFileName());
        String message;
        if (problem instanceof CircuitFormatException format) {
            message = switch (format.reason()) {
                case READ_ERROR -> Messages.format(LogisimImportTexts.PROBLEM_UNREADABLE, name);
                case NOT_XML -> Messages.format(LogisimImportTexts.PROBLEM_NOT_XML, name);
                case NOT_LOGISIM_PROJECT -> Messages.format(LogisimImportTexts.PROBLEM_NOT_LOGISIM, name);
                default -> Messages.format(LogisimImportTexts.PROBLEM_UNEXPECTED, name);
            };
        } else {
            message = Messages.format(LogisimImportTexts.PROBLEM_UNEXPECTED, name);
        }
        String details = problem.getMessage() + (problem.getCause() == null ? "" : "\n" + problem.getCause());
        dialogs.problem(view, Messages.text(LogisimImportTexts.PROBLEM_TITLE), message, details);
    }

    /** Runs on the worker: reads and converts the file. */
    private static ImportResult load(Path file) throws CircuitFormatException {
        try (InputStream input = Files.newInputStream(file)) {
            return LogisimImporter.importFrom(input, BASE_REGISTRY);
        } catch (IOException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, exception.toString(),
                    exception);
        }
    }

    /** The file's name without its {@code .circ}: what the window shows until the document is saved. */
    static String documentName(Path file) {
        String name = String.valueOf(file.getFileName());
        return name.toLowerCase(Locale.ROOT).endsWith(".circ") ? name.substring(0, name.length() - ".circ".length())
                : name;
    }

    private static Optional<Path> chooseFile(Component parent, Path directory) {
        return FileDialogs.chooseOpen(parent, directory, Messages.text(LogisimImportTexts.CHOOSE_TITLE),
                new FileNameExtensionFilter(Messages.text(LogisimImportTexts.CHOOSE_FILTER), "circ"));
    }

    private static Optional<WindowCommands> windowCommandsOf(Component component) {
        return SwingUtilities.getWindowAncestor(component) instanceof WindowCommands commands
                ? Optional.of(commands) : Optional.empty();
    }
}
