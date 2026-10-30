package cl.almejo.vsim.core.terminal;

import java.util.Objects;
import java.util.Optional;

/**
 * The look a cell was written with (SGR, spec §6). An absent colour means the screen's default text or background.
 * Bold is kept as a flag and never changes the stored colour: brightening colours 0–7 is a paint decision.
 */
public record CellStyle(Optional<ColorReference> foreground, Optional<ColorReference> background, boolean bold,
                        boolean faint, boolean underline, boolean reverse) {

    /** The default style: screen colours, no attribute. */
    public static final CellStyle PLAIN = new CellStyle(Optional.empty(), Optional.empty(), false, false, false,
            false);

    public CellStyle {
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(background, "background");
    }

    CellStyle withForeground(Optional<ColorReference> newForeground) {
        return new CellStyle(newForeground, background, bold, faint, underline, reverse);
    }

    CellStyle withBackground(Optional<ColorReference> newBackground) {
        return new CellStyle(foreground, newBackground, bold, faint, underline, reverse);
    }

    CellStyle withBold(boolean newBold) {
        return new CellStyle(foreground, background, newBold, faint, underline, reverse);
    }

    CellStyle withFaint(boolean newFaint) {
        return new CellStyle(foreground, background, bold, newFaint, underline, reverse);
    }

    CellStyle withUnderline(boolean newUnderline) {
        return new CellStyle(foreground, background, bold, faint, newUnderline, reverse);
    }

    CellStyle withReverse(boolean newReverse) {
        return new CellStyle(foreground, background, bold, faint, underline, newReverse);
    }

    /** The style an erased cell takes: only the background stays, as xterm erases with the current background. */
    CellStyle erasing() {
        return background.isEmpty() ? PLAIN : new CellStyle(Optional.empty(), background, false, false, false, false);
    }
}
