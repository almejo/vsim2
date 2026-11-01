package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.memory.MemoryLinkPaths;
import cl.almejo.vsim.core.terminal.ExpectedOutput;
import cl.almejo.vsim.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * The «Salida esperada» tab (spec §8.9, mockup §5): the expected text (written in the part or read from a .txt off
 * the EDT), what arrived so far (the transcript) and a sentence saying how they compare ({@link ExpectedOutput}, the
 * comparison the AI also uses). A file is read again when the part's expected-output parameters change. EDT only.
 */
final class ExpectedOutputPanel extends JPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpectedOutputPanel.class);
    private static final int TEXT_ROWS = 8;

    /** Where the expected text comes from, as the parameters say; a change reads it again. */
    private record Source(String kind, String text, String file, Optional<Path> circuitFile) {
    }

    private final Executor files;
    private final Executor edt;
    private final JTextArea expectedArea = textArea();
    private final JTextArea actualArea = textArea();
    private final JLabel status = new JLabel(" ");
    private Source source;
    private Optional<String> expected = Optional.empty();
    private String problem = "";
    private String transcript = "";
    /** Counts the reads, so a slow read of an older file never overwrites a newer one. */
    private long readGeneration;

    ExpectedOutputPanel(Executor files, Executor edt) {
        super(new BorderLayout());
        this.files = Objects.requireNonNull(files, "files");
        this.edt = Objects.requireNonNull(edt, "edt");
        JPanel texts = new JPanel(new GridLayout(1, 2));
        texts.add(labelled("terminal.expected.expectedLabel", expectedArea));
        texts.add(labelled("terminal.expected.actualLabel", actualArea));
        add(texts, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    /** The part's resolved parameters; a file link is resolved against the circuit's folder. */
    void parametersChanged(Map<String, Object> resolved, Optional<Path> circuitFile) {
        Source newSource = new Source(String.valueOf(resolved.get(TerminalTypes.EXPECTED)),
                String.valueOf(resolved.getOrDefault(TerminalTypes.EXPECTED_TEXT, "")),
                String.valueOf(resolved.getOrDefault(TerminalTypes.EXPECTED_FILE, "")), circuitFile);
        if (newSource.equals(source)) {
            return;
        }
        source = newSource;
        readGeneration++;
        expected = Optional.empty();
        problem = "";
        switch (newSource.kind()) {
            case TerminalTypes.EXPECTED_WRITTEN -> expected = Optional.of(newSource.text());
            case TerminalTypes.EXPECTED_FROM_FILE -> readFile(newSource);
            default -> problem = ExpectedOutputText.none();
        }
        refresh();
    }

    /** The text received since the last «Reiniciar». */
    void showTranscript(String newTranscript) {
        if (newTranscript.equals(transcript)) {
            return;
        }
        transcript = newTranscript;
        refresh();
    }

    String statusText() {
        return status.getText();
    }

    String expectedText() {
        return expectedArea.getText();
    }

    String actualText() {
        return actualArea.getText();
    }

    private void readFile(Source fileSource) {
        if (fileSource.file().isBlank()) {
            problem = ExpectedOutputText.noFile();
            return;
        }
        Path file;
        try {
            file = MemoryLinkPaths.resolve(fileSource.file(), fileSource.circuitFile());
        } catch (InvalidPathException exception) {
            problem = ExpectedOutputText.unreadable(fileSource.file());
            return;
        }
        long generation = readGeneration;
        String shown = MemoryLinkPaths.displayPath(fileSource.file(), fileSource.circuitFile());
        files.execute(() -> {
            Optional<String> text = read(file);
            edt.execute(() -> fileRead(generation, text, shown));
        });
    }

    private static Optional<String> read(Path file) {
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            LOGGER.warn("Could not read the expected output {}", file, exception);
            return Optional.empty();
        }
    }

    private void fileRead(long generation, Optional<String> text, String shownPath) {
        if (generation != readGeneration) {
            return;
        }
        expected = text;
        problem = text.isPresent() ? "" : ExpectedOutputText.unreadable(shownPath);
        refresh();
    }

    private void refresh() {
        expectedArea.setText(expected.orElse(""));
        actualArea.setText(transcript);
        status.setText(expected.map(text -> ExpectedOutputText.message(ExpectedOutput.compare(text, transcript)))
                .orElse(problem.isEmpty() ? " " : problem));
    }

    private static JTextArea textArea() {
        JTextArea area = new JTextArea(TEXT_ROWS, 0);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
        return area;
    }

    private static JPanel labelled(String key, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(Messages.text(key)), BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        return panel;
    }
}
