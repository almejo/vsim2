package cl.almejo.vsim.ui.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.logisim.ExportResult;
import cl.almejo.vsim.core.io.logisim.ImportNote;
import cl.almejo.vsim.core.io.logisim.LogisimExporter;
import cl.almejo.vsim.core.io.logisim.LogisimNotes;
import cl.almejo.vsim.core.io.logisim.LogisimWriter;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.FileNames;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.export.FileExportFeatures;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * «Circuito de Logisim (.circ) (beta)…» of one editor view: its item in Archivo ▸ Exportar after the other exports
 * (off for an empty document) and its shortcut registration ({@code file.exportLogisim}, no default stroke). It asks for the file,
 * converts a copy of the circuit and writes it off the event thread ({@link LogisimExporter}, {@link LogisimWriter}),
 * then lists what Logisim could not take in the import's report, each part by its name. The document is not touched.
 */
final class LogisimExportFeatures extends FileExportFeatures {

    static final String SHORTCUT_ID = "file.exportLogisim";
    private static final String EXPORT_PDF_ID = "file.exportPdf";
    private static final String EXPORT_IMAGE_ID = "file.exportImage";
    private static final Logger LOGGER = LoggerFactory.getLogger(LogisimExportFeatures.class);
    /** The standard parts: the document's own components travel in its copy. */
    private static final ComponentTypeRegistry BASE_REGISTRY = StandardComponentTypes.registry();

    private final LogisimExportExtension.Chooser chooser;
    private final LogisimImportDialogs dialogs;
    private final Executor worker;
    private final Executor eventThread;

    LogisimExportFeatures(EditorView view, LogisimExportExtension.Chooser chooser, LogisimImportDialogs dialogs,
                          Executor worker, Executor eventThread) {
        // After «PDF…», else after «Imagen…» in Archivo ▸ Exportar, else last.
        super(view, LogisimExportTexts.ACTION, LogisimExportTexts.MENU_ITEM, ExportIcons.EXPORT_LOGISIM, SHORTCUT_ID,
                view.shortcutActions().get(EXPORT_PDF_ID), view.shortcutActions().get(EXPORT_IMAGE_ID));
        this.chooser = chooser;
        this.dialogs = dialogs;
        this.worker = worker;
        this.eventThread = eventThread;
    }

    @Override
    protected void export() {
        if (!available()) {
            return;
        }
        EditorView view = view();
        OpenDocument openDocument = view.openDocument();
        AppPreferences preferences = view.services().preferences();
        Optional<Path> target = chooser.choose(view, startDirectory(preferences.exportDirectory()),
                FileNames.safeName(openDocument.displayName()) + LogisimExportExtension.EXTENSION);
        if (target.isEmpty()) {
            return;
        }
        Path folder = target.get().toAbsolutePath().getParent();
        if (folder != null) {
            preferences.setExportDirectory(folder);
        }
        CircuitDocument copy = openDocument.document().copy();
        // Logisim's main circuit takes the circuit's own name; the file is named after the window.
        copy.setName(openDocument.circuitName());
        worker.execute(() -> write(copy, target.get()));
    }

    /** Runs on the worker: converts and writes, then tells the user on the event thread. */
    private void write(CircuitDocument copy, Path target) {
        ExportResult result;
        try {
            result = LogisimExporter.exportDocument(copy, BASE_REGISTRY);
            LogisimWriter.write(result.project(), target);
        } catch (IOException | RuntimeException | Error exception) {
            LOGGER.warn("Could not export {} to Logisim", target, exception);
            eventThread.execute(() -> dialogs.problem(view(), Messages.text(LogisimExportTexts.PROBLEM_TITLE),
                    Messages.format(LogisimExportTexts.PROBLEM_UNWRITABLE, String.valueOf(target.getFileName())),
                    String.valueOf(exception.getMessage())));
            return;
        }
        List<ImportNote> notes = result.notes();
        String mainCircuit = result.project().mainCircuit();
        eventThread.execute(() -> report(target, notes, mainCircuit));
    }

    private void report(Path target, List<ImportNote> notes, String mainCircuit) {
        ReportHost host = new EditorReportHost(Optional.of(view()), view().services().preferences());
        ImportReport report = ImportReport.of(notes.stream().map(LogisimExportFeatures::named).toList(),
                host, mainCircuit);
        if (!report.isEmpty()) {
            dialogs.report(view(), Messages.text(LogisimExportTexts.REPORT_TITLE),
                    Messages.format(LogisimExportTexts.REPORT_SUMMARY, String.valueOf(target.getFileName()),
                            report.noteCount()), report, host);
        }
    }

    /** A note about a part says the part's name (and its label), not its type id: «ALU «U1»». */
    private static ImportNote named(ImportNote note) {
        if (!LogisimNotes.EXPORT_PART_KEYS.contains(note.messageKey()) || note.arguments().size() < 2) {
            return note;
        }
        String kind = Messages.componentName(note.arguments().get(0));
        String label = note.arguments().get(1);
        List<String> arguments = new ArrayList<>();
        arguments.add(label.isBlank() ? kind : Messages.format(LogisimExportTexts.PART_WITH_LABEL, kind, label));
        arguments.addAll(note.arguments().subList(2, note.arguments().size()));
        return note.withArguments(arguments);
    }
}
