package cl.almejo.vsim.ui.paint;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The font of component labels, free texts and frame titles (M6.8, Preferencias ▸ General): a family and a size.
 * A family that is not installed here (preferences copied from another computer) paints in SansSerif; the record
 * keeps the name, so the preference is not lost.
 */
public record TextFont(String family, int size) {

    public static final int MINIMUM_SIZE = 8;
    public static final int MAXIMUM_SIZE = 24;
    public static final TextFont DEFAULT = new TextFont(Font.SANS_SERIF, 10);
    /** The height of a frame's title band in document pixels ({@code FramePainter.TITLE_BAR_HEIGHT}). */
    public static final double TITLE_BAND_HEIGHT = 16;

    public TextFont {
        Objects.requireNonNull(family, "family");
        if (size < MINIMUM_SIZE || size > MAXIMUM_SIZE) {
            throw new IllegalArgumentException("text font size out of range: " + size);
        }
    }

    /** Labels and free texts: the family at the size, plain. */
    public Font labelFont() {
        return new Font(installedFamily(), Font.PLAIN, size);
    }

    /**
     * Frame titles: the family, bold, one point larger than the labels, but never taller (ascent + descent) than
     * {@link #TITLE_BAND_HEIGHT}: past that size the title stops growing, so it is never clipped by its band. Made
     * once per font change ({@link TextFonts}), not per paint.
     */
    public Font titleFont() {
        String installed = installedFamily();
        FontRenderContext context = new FontRenderContext(null, true, true);
        for (int titleSize = size + 1; titleSize > MINIMUM_SIZE; titleSize--) {
            Font candidate = new Font(installed, Font.BOLD, titleSize);
            LineMetrics metrics = candidate.getLineMetrics("Ag", context);
            if (metrics.getAscent() + metrics.getDescent() <= TITLE_BAND_HEIGHT) {
                return candidate;
            }
        }
        return new Font(installed, Font.BOLD, MINIMUM_SIZE);
    }

    /** Small tags drawn on the board (bus values, pin widths, hover tags): the family, bold, one point smaller. */
    public Font tagFont() {
        return new Font(installedFamily(), Font.BOLD, size - 1);
    }

    /** The family painted with: the chosen one when installed, otherwise SansSerif. */
    public String installedFamily() {
        return isInstalled(family) ? family : Font.SANS_SERIF;
    }

    /** Whether {@code family} is a font family of this computer (Java's logical families included). */
    public static boolean isInstalled(String family) {
        return InstalledFamilies.LOWER_CASE.contains(family.toLowerCase(Locale.ROOT));
    }

    /** The font families of this computer, in the order the system gives them. */
    public static List<String> installedFamilies() {
        return InstalledFamilies.NAMES;
    }

    /** Asked once, the first time a font is needed: listing the families can take a moment. */
    private static final class InstalledFamilies {
        private static final List<String> NAMES = List.of(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        private static final Set<String> LOWER_CASE = NAMES.stream()
                .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
}
