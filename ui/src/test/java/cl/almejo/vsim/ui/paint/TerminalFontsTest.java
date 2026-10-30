package cl.almejo.vsim.ui.paint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.Font;
import java.awt.font.FontRenderContext;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalFontsTest {

    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private static final double[][] CELLS = {{6, 12}, {8, 16}, {10, 20}};

    @Test
    void theBundledFontsAreLoadedFromTheJarResources() {
        assertThat(TerminalFonts.font(TerminalFontFamily.PLEX_MONO, false).getFamily()).contains("Plex");
        assertThat(TerminalFonts.font(TerminalFontFamily.PLEX_MONO, true).getFontName()).contains("Bold");
        assertThat(TerminalFonts.font(TerminalFontFamily.VT323, false).getFamily()).contains("VT323");
    }

    @ParameterizedTest
    @EnumSource(TerminalFontFamily.class)
    void everyFamilyIsMonospaced(TerminalFontFamily family) {
        Font font = TerminalFonts.font(family, false).deriveFont(40f);

        double narrow = font.getStringBounds("i", CONTEXT).getWidth();
        double wide = font.getStringBounds("W", CONTEXT).getWidth();
        assertThat(narrow).isEqualTo(wide);
    }

    @ParameterizedTest
    @EnumSource(TerminalFontFamily.class)
    void aFittedFontFitsTheCell(TerminalFontFamily family) {
        for (boolean bold : new boolean[] {false, true}) {
            for (double[] cell : CELLS) {
                Font font = TerminalFonts.fitted(family, bold, cell[0], cell[1], CONTEXT);

                assertThat(font.getStringBounds("M", CONTEXT).getWidth()).isLessThanOrEqualTo(cell[0]);
                assertThat((double) font.getLineMetrics("Mg|", CONTEXT).getHeight()).isLessThanOrEqualTo(cell[1]);
                assertThat(font.getSize2D()).isGreaterThan(2f);
            }
        }
    }

    @Test
    void aFittedFontIsCachedPerCellSize() {
        Font first = TerminalFonts.fitted(TerminalFontFamily.PLEX_MONO, false, 8, 16, CONTEXT);

        assertThat(TerminalFonts.fitted(TerminalFontFamily.PLEX_MONO, false, 8, 16, CONTEXT)).isSameAs(first);
        assertThat(TerminalFonts.fitted(TerminalFontFamily.PLEX_MONO, false, 16, 32, CONTEXT).getSize2D())
                .isGreaterThan(first.getSize2D());
    }
}
