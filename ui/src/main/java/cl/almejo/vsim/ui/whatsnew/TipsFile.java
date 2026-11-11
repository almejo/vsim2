package cl.almejo.vsim.ui.whatsnew;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The tips of the day, read from {@value #RESOURCE}: one tip per line in the order they are shown, seven columns
 * separated by «|» (id, kind, title key, text key, pictures, key, try); «#» starts a comment line. The pictures are
 * resources under {@value #FOLDER}, separated by «,»; the key is the {@code {…}} name VSim draws big in the picture, or
 * «-»; try is «-» (no «Probarlo»), «circuit» (open the tip's circuit) or a {@code ShortcutCatalog} id (open it and run
 * that action). Adding a tip is a line here, its pictures and its two texts (spec: no fixed number of tips).
 */
final class TipsFile {

    /** Where the tips' file and pictures live on the classpath. */
    static final String FOLDER = "/tips/";
    static final String RESOURCE = FOLDER + "tips.txt";

    private static final String COLUMN_SEPARATOR = "\\|";
    private static final String PICTURE_SEPARATOR = ",";
    private static final String COMMENT = "#";
    private static final String NONE = "-";
    private static final String OPEN_THE_CIRCUIT = "circuit";
    private static final int COLUMNS = 7;

    private TipsFile() {
    }

    /** The tips bundled with VSim. */
    static List<Tip> load() {
        try (InputStream stream = TipsFile.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            return parse(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().toList());
        } catch (IOException exception) {
            throw new UncheckedIOException("could not read " + RESOURCE, exception);
        }
    }

    /** The tips of {@code lines}; IllegalArgumentException naming the line when one is broken. */
    static List<Tip> parse(List<String> lines) {
        List<Tip> tips = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (line.isEmpty() || line.startsWith(COMMENT)) {
                continue;
            }
            Tip tip = tip(line, index + 1);
            if (!ids.add(tip.id())) {
                throw broken(index + 1, "repeated id " + tip.id());
            }
            tips.add(tip);
        }
        return List.copyOf(tips);
    }

    private static Tip tip(String line, int number) {
        String[] columns = line.split(COLUMN_SEPARATOR);
        if (columns.length != COLUMNS) {
            throw broken(number, "expected " + COLUMNS + " columns: " + line);
        }
        for (int column = 0; column < COLUMNS; column++) {
            columns[column] = columns[column].strip();
            if (columns[column].isEmpty()) {
                throw broken(number, "empty column " + (column + 1));
            }
        }
        TipKind kind = TipKind.of(columns[1]).orElseThrow(() -> broken(number, "unknown kind " + columns[1]));
        List<String> pictures = Arrays.stream(columns[4].split(PICTURE_SEPARATOR)).map(String::strip).toList();
        if (!kind.accepts(pictures)) {
            throw broken(number, "the pictures of a " + columns[1] + " tip: " + columns[4]);
        }
        Tip tip = new Tip(columns[0], kind, columns[2], columns[3], pictures, optional(columns[5]),
                optional(columns[6]).map(TipsFile::trial));
        if (tip.trial().isPresent() && tip.circuit().isEmpty()) {
            throw broken(number, "«Probarlo» needs a circuit to open");
        }
        return tip;
    }

    private static Optional<String> optional(String column) {
        return column.equals(NONE) ? Optional.empty() : Optional.of(column);
    }

    private static Tip.Trial trial(String column) {
        return new Tip.Trial(column.equals(OPEN_THE_CIRCUIT) ? Optional.empty() : Optional.of(column));
    }

    private static IllegalArgumentException broken(int number, String problem) {
        return new IllegalArgumentException("line " + number + ": " + problem);
    }
}
