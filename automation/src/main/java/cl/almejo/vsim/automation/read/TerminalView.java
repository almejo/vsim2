package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a Terminal of the running simulation shows: its {@code screen} (one text per row, trailing spaces trimmed,
 * always {@code rows} of them), the cursor, the {@code mode} ({@code LOGISIM}, {@code VT} or {@code TELETYPE}) and the
 * {@code profile} the part was set from, the last {@code history} rows that scrolled off (oldest first), the last
 * received {@code bytes} (oldest first) and, when the part has an expected output, whether the transcript
 * {@code matches} it or {@code differs} ({@code file}: it comes from a file the AI may not read). {@code time} is the
 * simulation time of the reading.
 */
public record TerminalView(DocumentId document, DocumentVersion version, String component, String mode,
                           String profile, int rows, int columns, int cursorRow, int cursorColumn,
                           boolean cursorVisible, List<String> screen, List<String> history, List<ByteView> bytes,
                           Optional<String> expected, long time) {

    /** One received byte: when, as hex, and as the character it stands for (control codes escaped). */
    public record ByteView(long time, String hex, String character) {
    }

    public TerminalView {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(expected, "expected");
        screen = List.copyOf(screen);
        history = List.copyOf(history);
        bytes = List.copyOf(bytes);
    }
}
