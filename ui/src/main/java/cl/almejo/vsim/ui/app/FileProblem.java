package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.VsimVersion;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.ui.i18n.Messages;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Why one file could not be opened, in plain words: {@code message} names the file and says what is wrong and what to
 * do; {@code details} keeps the technical text (the exception's) for a «Detalles» part of the dialog.
 */
record FileProblem(String message, String details) {

    /** Every bundle key this class can ask for. */
    static final List<String> MESSAGE_KEYS = List.of("fileproblem.title", "fileproblem.title.many",
            "fileproblem.newer", "fileproblem.newer.unknown", "fileproblem.notVsim", "fileproblem.logisim",
            "fileproblem.damaged", "fileproblem.damaged.at", "fileproblem.unsupported", "fileproblem.unreadable",
            "fileproblem.element.components", "fileproblem.element.wires", "fileproblem.element.labels",
            "fileproblem.element.frames", "fileproblem.element.stickyNotes", "fileproblem.element.other",
            "fileproblem.details");

    /** How much of the start of the file is looked at to tell what it seems to be. */
    private static final int SNIFFED_CHARACTERS = 2000;

    static FileProblem of(Path file, CircuitFormatException exception) {
        String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        String details = exception.getMessage() + (exception.getCause() == null ? ""
                : "\n" + exception.getCause());
        String message = switch (exception.reason()) {
            case NEWER_FORMAT -> newer(name, exception);
            case NOT_VSIM_FILE -> notVsim(name, file);
            case INVALID_JSON -> looksLikeVsim2(sniff(file)) ? damaged(name, exception) : notVsim(name, file);
            case READ_ERROR -> Messages.format("fileproblem.unreadable", name);
            case UNSUPPORTED_VERSION -> Messages.format("fileproblem.unsupported", name);
            case INVALID_ELEMENT, UNKNOWN_COMPONENT_TYPE -> damaged(name, exception);
        };
        return new FileProblem(message, details);
    }

    private static String newer(String name, CircuitFormatException exception) {
        String current = VsimVersion.current();
        return exception.savedWith() == null
                ? Messages.format("fileproblem.newer.unknown", name, exception.detail(), current)
                : Messages.format("fileproblem.newer", name, exception.savedWith(), current);
    }

    private static String notVsim(String name, Path file) {
        String start = sniff(file).stripLeading();
        boolean xml = start.startsWith("<");
        return xml && start.toLowerCase(Locale.ROOT).contains("logisim")
                ? Messages.format("fileproblem.logisim", name)
                : Messages.format("fileproblem.notVsim", name);
    }

    private static String damaged(String name, CircuitFormatException exception) {
        CircuitFormatException.Element element = exception.element();
        if (element == null) {
            return Messages.format("fileproblem.damaged", name);
        }
        return Messages.format("fileproblem.damaged.at", name, elementWords(element));
    }

    private static String elementWords(CircuitFormatException.Element element) {
        String key = "fileproblem.element." + element.kind();
        String id = String.valueOf(element.id());
        if (element.type() != null) {
            return Messages.format(key, id, Messages.componentName(element.type()));
        }
        return Messages.format(Messages.bundle(Messages.locale()).containsKey(key) ? key
                : "fileproblem.element.other", id);
    }

    /** What a damaged VSim 2 file still says at its start; a truncated one is not JSON any more. */
    private static boolean looksLikeVsim2(String start) {
        String compact = start.replaceAll("\\s", "");
        return compact.contains("\"format\":\"vsim2\"") || compact.contains("\"formatVersion\":");
    }

    private static String sniff(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            char[] buffer = new char[SNIFFED_CHARACTERS];
            int count = reader.read(buffer);
            return count <= 0 ? "" : new String(buffer, 0, count);
        } catch (IOException | RuntimeException exception) {
            return ""; // unreadable or not text: it only changes the wording
        }
    }
}
