package cl.almejo.vsim.ui.whatsnew;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The «Qué hay de nuevo» bubbles of one release, in order, read from {@code whatsnew/<version>.txt}: one bubble per
 * line, four columns separated by «|» (target id, title key, text key, action or «-»); «#» starts a comment line. The
 * action is a {@code ShortcutCatalog} id, optionally with «#id» for its palette entry of that id.
 */
record NewsFile(String version, List<NewsStep> steps) {

    private static final String COLUMN_SEPARATOR = "\\|";
    private static final String COMMENT = "#";
    private static final String NO_ACTION = "-";
    private static final String ENTRY_SEPARATOR = "#";
    private static final int COLUMNS = 4;

    NewsFile {
        Objects.requireNonNull(version, "version");
        steps = List.copyOf(steps);
    }

    /** The bubbles of {@code lines}; IllegalArgumentException naming the line when one is broken. */
    static NewsFile parse(String version, List<String> lines) {
        List<NewsStep> steps = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (line.isEmpty() || line.startsWith(COMMENT)) {
                continue;
            }
            steps.add(step(line, index + 1));
        }
        return new NewsFile(version, steps);
    }

    private static NewsStep step(String line, int number) {
        String[] columns = line.split(COLUMN_SEPARATOR);
        if (columns.length != COLUMNS) {
            throw new IllegalArgumentException("line " + number + ": expected " + COLUMNS + " columns: " + line);
        }
        for (int column = 0; column < COLUMNS; column++) {
            columns[column] = columns[column].strip();
            if (columns[column].isEmpty()) {
                throw new IllegalArgumentException("line " + number + ": empty column " + (column + 1));
            }
        }
        return new NewsStep(columns[0], columns[1], columns[2], action(columns[3], number));
    }

    private static Optional<NewsStep.ActionReference> action(String column, int number) {
        if (column.equals(NO_ACTION)) {
            return Optional.empty();
        }
        int separator = column.indexOf(ENTRY_SEPARATOR);
        if (separator < 0) {
            return Optional.of(new NewsStep.ActionReference(column, Optional.empty()));
        }
        String entry = column.substring(separator + 1).strip();
        if (entry.isEmpty()) {
            throw new IllegalArgumentException("line " + number + ": bad palette entry: " + column);
        }
        return Optional.of(new NewsStep.ActionReference(column.substring(0, separator).strip(), Optional.of(entry)));
    }
}
