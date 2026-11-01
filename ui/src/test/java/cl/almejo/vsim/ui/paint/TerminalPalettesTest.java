package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.terminal.ColorReference;
import cl.almejo.vsim.ui.paint.TerminalPalettes.AnsiPalette;
import cl.almejo.vsim.ui.paint.TerminalPalettes.ScreenColors;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/** The screen colours and ANSI palettes are the mockup's {@code SCHEMES} and {@code ANSI_PALETTES}, value by value. */
class TerminalPalettesTest {

    @Test
    void theScreenColoursAreThePhosphorsOfTheSpec() {
        assertThat(ScreenColors.of("green").text(ColorScheme.LIGHT)).isEqualTo(new Color(0x33FF66));
        assertThat(ScreenColors.of("green").background(ColorScheme.LIGHT)).isEqualTo(new Color(0x0B130D));
        assertThat(ScreenColors.of("amber").text(ColorScheme.LIGHT)).isEqualTo(new Color(0xFFB000));
        assertThat(ScreenColors.of("amber").background(ColorScheme.LIGHT)).isEqualTo(new Color(0x140D00));
        assertThat(ScreenColors.of("white").text(ColorScheme.LIGHT)).isEqualTo(new Color(0xE8E8E8));
        assertThat(ScreenColors.of("white").background(ColorScheme.LIGHT)).isEqualTo(new Color(0x101010));
        assertThat(ScreenColors.of("paper").text(ColorScheme.LIGHT)).isEqualTo(new Color(0x2A2A2A));
        assertThat(ScreenColors.of("paper").background(ColorScheme.LIGHT)).isEqualTo(new Color(0xF6F1E3));
        assertThat(ScreenColors.of("unknown")).isEqualTo(ScreenColors.WHITE);
    }

    @Test
    void theThemeColoursAreReadFromTheSchemeEachTime() {
        ColorScheme custom = ColorScheme.LIGHT.with(ColorRole.TEXT, new Color(0x123456))
                .with(ColorRole.BACKGROUND, new Color(0x654321));
        assertThat(ScreenColors.THEME.text(custom)).isEqualTo(new Color(0x123456));
        assertThat(ScreenColors.THEME.background(custom)).isEqualTo(new Color(0x654321));
        assertThat(ScreenColors.THEME.text(ColorSchemes.DARK)).isEqualTo(ColorSchemes.DARK.text());
    }

    @Test
    void theSixteenColoursOfEachPaletteAreTheMockups() {
        assertThat(AnsiPalette.of("xterm").color(1)).isEqualTo(new Color(0xCD0000));
        assertThat(AnsiPalette.of("xterm").color(12)).isEqualTo(new Color(0x5C5CFF));
        assertThat(AnsiPalette.of("vga").color(3)).isEqualTo(new Color(0xAA5500));
        assertThat(AnsiPalette.of("vga").color(8)).isEqualTo(new Color(0x555555));
        assertThat(AnsiPalette.of("solarized").color(0)).isEqualTo(new Color(0x073642));
        assertThat(AnsiPalette.of("solarized").color(15)).isEqualTo(new Color(0xFDF6E3));
        assertThat(AnsiPalette.of("colorblind").color(1)).isEqualTo(new Color(0xD55E00));
        assertThat(AnsiPalette.of("colorblind").color(14)).isEqualTo(new Color(0x8FD0F5));
        assertThat(AnsiPalette.of("unknown")).isEqualTo(AnsiPalette.XTERM);
    }

    @Test
    void indexesAbove15AreTheXtermCubeAndGrayRampAndRgbIsLiteral() {
        assertThat(TerminalPalettes.color256(16)).isEqualTo(new Color(0, 0, 0));
        assertThat(TerminalPalettes.color256(196)).isEqualTo(new Color(255, 0, 0));
        assertThat(TerminalPalettes.color256(110)).isEqualTo(new Color(135, 175, 215));
        assertThat(TerminalPalettes.color256(231)).isEqualTo(new Color(255, 255, 255));
        assertThat(TerminalPalettes.color256(232)).isEqualTo(new Color(8, 8, 8));
        assertThat(TerminalPalettes.color256(255)).isEqualTo(new Color(238, 238, 238));
        assertThat(TerminalPalettes.resolve(new ColorReference.Rgb(0x0A0B0C), AnsiPalette.VGA, false))
                .isEqualTo(new Color(0x0A0B0C));
        assertThat(TerminalPalettes.resolve(new ColorReference.PaletteIndex(200), AnsiPalette.VGA, false))
                .isEqualTo(TerminalPalettes.color256(200));
    }

    @Test
    void boldBrightensOnlyTheFirstEightColours() {
        assertThat(TerminalPalettes.resolve(new ColorReference.PaletteIndex(1), AnsiPalette.XTERM, true))
                .isEqualTo(new Color(0xFF0000));
        assertThat(TerminalPalettes.resolve(new ColorReference.PaletteIndex(1), AnsiPalette.XTERM, false))
                .isEqualTo(new Color(0xCD0000));
        assertThat(TerminalPalettes.resolve(new ColorReference.PaletteIndex(9), AnsiPalette.XTERM, true))
                .isEqualTo(new Color(0xFF0000));
        assertThat(TerminalPalettes.resolve(new ColorReference.PaletteIndex(100), AnsiPalette.XTERM, true))
                .isEqualTo(TerminalPalettes.color256(100));
    }
}
