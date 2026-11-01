package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.ui.paint.TerminalPalettes.AnsiPalette;
import cl.almejo.vsim.ui.paint.TerminalPalettes.ScreenColors;

import java.util.Map;
import java.util.Objects;

/**
 * The «Aspecto» parameters of a Terminal as the painter and the view's animation read them (spec §8.1–§8.5). All of
 * them are display-only: changing one repaints, never rebuilds the screen.
 *
 * @param screenColors «Colores de pantalla»
 * @param palette      «Paleta ANSI»
 * @param effect       «Efecto de pantalla»
 * @param font         «Fuente»
 * @param cursor       «Cursor»
 * @param blink        «Parpadea»
 * @param markLast     «Marcar el último carácter»
 */
public record TerminalLook(ScreenColors screenColors, AnsiPalette palette, TerminalEffect effect,
                           TerminalFontFamily font, TerminalCursorShape cursor, boolean blink, boolean markLast) {

    public TerminalLook {
        Objects.requireNonNull(screenColors, "screenColors");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(cursor, "cursor");
    }

    /** The look the {@code resolved} parameters of a Terminal choose. */
    public static TerminalLook of(Map<String, Object> resolved) {
        return new TerminalLook(ScreenColors.of(resolved.get(TerminalTypes.SCREEN_COLORS)),
                AnsiPalette.of(resolved.get(TerminalTypes.ANSI_PALETTE)),
                TerminalEffect.of(resolved.get(TerminalTypes.EFFECT)), fontFamily(resolved.get(TerminalTypes.FONT)),
                TerminalCursorShape.of(resolved.get(TerminalTypes.CURSOR)),
                Boolean.TRUE.equals(resolved.get(TerminalTypes.BLINK)),
                Boolean.TRUE.equals(resolved.get(TerminalTypes.MARK_LAST)));
    }

    /** Whether the cursor blinks: «Parpadea» on a shape that blinks. */
    public boolean blinking() {
        return blink && cursor.blinks();
    }

    private static TerminalFontFamily fontFamily(Object value) {
        return switch (String.valueOf(value)) {
            case TerminalTypes.FONT_VT323 -> TerminalFontFamily.VT323;
            case TerminalTypes.FONT_SYSTEM -> TerminalFontFamily.SYSTEM;
            default -> TerminalFontFamily.PLEX_MONO;
        };
    }
}
