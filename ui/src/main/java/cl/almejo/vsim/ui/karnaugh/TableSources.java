package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.ImportedGrid;
import cl.almejo.vsim.core.logic.TableImport;
import cl.almejo.vsim.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Where an imported table comes from (spec §6): a file the user picks ({@code .xlsx}, first sheet, or {@code .csv}
 * with commas, semicolons or tabs) or the text the clipboard holds (cells copied in Excel or LibreOffice are tab
 * separated). The chooser and the clipboard are injected, so tests never show a dialog nor touch the system clipboard.
 *
 * @param chooser   asks for a file over a component; empty when the user cancels
 * @param clipboard the clipboard's text, empty when it holds none
 */
public record TableSources(Chooser chooser, Supplier<Optional<String>> clipboard) {

    private static final Logger LOGGER = LoggerFactory.getLogger(TableSources.class);

    @FunctionalInterface
    public interface Chooser {
        Optional<Path> choose(Component parent);
    }

    /** Shows a file chooser over a component and answers how it was closed ({@link JFileChooser#APPROVE_OPTION}…). */
    @FunctionalInterface
    interface ChooserDialog {
        int show(JFileChooser chooser, Component parent);
    }

    /**
     * The file chooser and the system clipboard. The chooser opens in {@code startDirectory} (the folder «Abrir»
     * uses) and hands the chosen file to {@code chosen}, so the next «Abrir» or import starts there too.
     */
    public static TableSources system(Supplier<Path> startDirectory, Consumer<Path> chosen) {
        return new TableSources(fileChooser(startDirectory, chosen, (chooser, parent) -> chooser.showOpenDialog(parent)),
                TableSources::clipboardText);
    }

    static Chooser fileChooser(Supplier<Path> startDirectory, Consumer<Path> chosen, ChooserDialog dialog) {
        return parent -> {
            JFileChooser chooser = new JFileChooser(startDirectory.get().toFile());
            chooser.setDialogTitle(Messages.text("tableImport.choose.title"));
            chooser.setFileFilter(new FileNameExtensionFilter(Messages.text("tableImport.choose.filter"),
                    "xlsx", "csv", "tsv", "txt"));
            if (dialog.show(chooser, parent) != JFileChooser.APPROVE_OPTION) {
                return Optional.empty();
            }
            Path file = chooser.getSelectedFile().toPath();
            chosen.accept(file);
            return Optional.of(file);
        };
    }

    /** Reads {@code file}: an {@code .xlsx} as a workbook, anything else as separated text (UTF-8, else Latin-1). */
    static ImportedGrid readFile(Path file) throws IOException {
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            try (InputStream input = Files.newInputStream(file)) {
                return TableImport.readXlsx(input);
            }
        }
        TableImport.checkSize(Files.size(file)); // read whole: a huge file must not run out of memory
        return TableImport.readCsv(decoded(Files.readAllBytes(file)));
    }

    /** Pasted cells: tab separated when they have tabs, otherwise read as a CSV line by line. */
    static ImportedGrid readPasted(String text) {
        return text.indexOf('\t') >= 0 ? TableImport.readTabSeparated(text) : TableImport.readCsv(text);
    }

    /** The title proposed for a file: its name without the extension. */
    static String titleOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Excel saves CSV in the system's code page: text that is not valid UTF-8 is read as Latin-1. */
    private static String decoded(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static Optional<String> clipboardText() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return data instanceof String text ? Optional.of(text) : Optional.empty();
        } catch (Exception exception) {
            // Nothing usable there (an image, a file list, a clipboard another program holds): nothing to paste.
            LOGGER.debug("The clipboard holds no text: {}", exception.getMessage());
            return Optional.empty();
        }
    }
}
