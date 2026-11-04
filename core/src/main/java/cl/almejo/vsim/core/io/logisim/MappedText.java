package cl.almejo.vsim.core.io.logisim;

import java.util.List;
import java.util.Objects;

/**
 * A Logisim {@code Text}, or a {@code Probe} ({@code probe}), which VSim does not have as a part: the bus already shows
 * its value, so only its label is kept as a text (decided in the spec). {@code horizontalAlignment} and
 * {@code verticalAlignment} are Logisim's ({@code left}/{@code center}/{@code right}, {@code top}/{@code base}/…); a
 * probe's are its label's place ({@code labelloc}) and {@code ""}.
 */
public record MappedText(String text, LogisimFont font, String horizontalAlignment, String verticalAlignment,
                         boolean probe, List<ImportNote> notes) implements LogisimMapping {

    public MappedText {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(horizontalAlignment, "horizontalAlignment");
        Objects.requireNonNull(verticalAlignment, "verticalAlignment");
        notes = List.copyOf(notes);
    }
}
