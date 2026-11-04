package cl.almejo.vsim.core.io.logisim;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** A Logisim font: family, bold, italic and size in points ({@code SansSerif bolditalic 14} in attributes). */
public record LogisimFont(String family, boolean bold, boolean italic, int size) {

    /** Logisim's label and text font, {@code SansSerif plain 12} (Logisim's default). */
    public static final LogisimFont DEFAULT = new LogisimFont("SansSerif", false, false, 12);

    public LogisimFont {
        Objects.requireNonNull(family, "family");
    }

    /**
     * The font an attribute writes as {@code family style size}, the style being {@code plain}, {@code bold},
     * {@code italic} or {@code bolditalic} (the way Logisim writes the style).
     *
     * @throws IllegalArgumentException when the text is not in that form
     */
    public static LogisimFont parse(String text) {
        String[] parts = text.strip().split("\\s+");
        if (parts.length < 3) {
            throw new IllegalArgumentException("not a font: " + text);
        }
        String style = parts[parts.length - 2].toLowerCase(Locale.ROOT);
        String family = String.join(" ", Arrays.asList(parts).subList(0, parts.length - 2));
        try {
            return new LogisimFont(family, style.contains("bold"), style.contains("italic"),
                    Integer.parseInt(parts[parts.length - 1]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("not a font: " + text, exception);
        }
    }
}
