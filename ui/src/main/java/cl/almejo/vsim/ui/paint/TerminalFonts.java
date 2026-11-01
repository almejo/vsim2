package cl.almejo.vsim.ui.paint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.font.FontRenderContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bundled terminal fonts (IBM Plex Mono and VT323, SIL OFL 1.1; see {@code fonts/OFL-*.txt}), loaded once and lazily
 * with {@link Font#createFont}. When a font cannot be loaded the logical monospaced font stands in (logged once).
 */
public final class TerminalFonts {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalFonts.class);
    private static final float REFERENCE_SIZE = 100f;
    private static final float MINIMUM_SIZE = 1f;
    private static final float SIZE_STEP = 0.25f;
    private static final String RESOURCE_FOLDER = "/fonts/";
    /** The letter whose advance is a cell's width. */
    static final String WIDEST_LETTER = "M";
    /** Letters that reach the top and the bottom of a line. */
    static final String TALL_SAMPLE = "Mg|";

    private record FamilyKey(TerminalFontFamily family, boolean bold) {
    }

    private record FitKey(TerminalFontFamily family, boolean bold, double width, double height) {
    }

    private static final Map<FamilyKey, Font> BASES = new ConcurrentHashMap<>();
    private static final Map<FitKey, Font> FITTED = new ConcurrentHashMap<>();
    private static final Set<TerminalFontFamily> REPORTED = EnumSet.noneOf(TerminalFontFamily.class);

    private TerminalFonts() {
    }

    /** The font at a reference size (derive another size from it); never null. */
    public static Font font(TerminalFontFamily family, boolean bold) {
        return BASES.computeIfAbsent(new FamilyKey(family, bold), key -> load(key.family(), key.bold()));
    }

    /**
     * The largest font whose advance fits {@code cellWidthPixels} and whose line height fits {@code cellHeightPixels}.
     * Cached per (family, weight, cell size).
     */
    public static Font fitted(TerminalFontFamily family, boolean bold, double cellWidthPixels,
                              double cellHeightPixels, FontRenderContext context) {
        FitKey key = new FitKey(family, bold, cellWidthPixels, cellHeightPixels);
        Font cached = FITTED.get(key);
        if (cached != null) {
            return cached;
        }
        Font reference = font(family, bold).deriveFont(REFERENCE_SIZE);
        double advance = reference.getStringBounds(WIDEST_LETTER, context).getWidth();
        double height = reference.getLineMetrics(TALL_SAMPLE, context).getHeight();
        double scale = Math.min(cellWidthPixels / advance, cellHeightPixels / height);
        float size = (float) Math.max(MINIMUM_SIZE, Math.floor(REFERENCE_SIZE * scale / SIZE_STEP) * SIZE_STEP);
        Font fitted = reference.deriveFont(size);
        while (size > MINIMUM_SIZE && !fits(fitted, cellWidthPixels, cellHeightPixels, context)) {
            size -= SIZE_STEP;
            fitted = reference.deriveFont(size);
        }
        FITTED.put(key, fitted);
        return fitted;
    }

    private static boolean fits(Font font, double width, double height, FontRenderContext context) {
        return font.getStringBounds(WIDEST_LETTER, context).getWidth() <= width
                && font.getLineMetrics(TALL_SAMPLE, context).getHeight() <= height;
    }

    private static Font load(TerminalFontFamily family, boolean bold) {
        String resource = switch (family) {
            case PLEX_MONO -> RESOURCE_FOLDER + (bold ? "IBMPlexMono-Bold.ttf" : "IBMPlexMono-Regular.ttf");
            case VT323 -> RESOURCE_FOLDER + "VT323-Regular.ttf";
            case SYSTEM -> null;
        };
        int style = bold ? Font.BOLD : Font.PLAIN;
        if (resource == null) {
            return new Font(Font.MONOSPACED, style, (int) REFERENCE_SIZE);
        }
        try (InputStream stream = TerminalFonts.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("missing resource " + resource);
            }
            Font loaded = Font.createFont(Font.TRUETYPE_FONT, stream);
            // VT323 has no bold face: ask for the synthetic one so the option still looks different.
            return family == TerminalFontFamily.VT323 && bold ? loaded.deriveFont(Font.BOLD) : loaded;
        } catch (IOException | FontFormatException | RuntimeException exception) {
            reportOnce(family, exception);
            return new Font(Font.MONOSPACED, style, (int) REFERENCE_SIZE);
        }
    }

    private static void reportOnce(TerminalFontFamily family, Exception exception) {
        synchronized (REPORTED) {
            if (REPORTED.add(family)) {
                LOGGER.warn("Could not load the {} font, using the system monospaced one", family, exception);
            }
        }
    }
}
