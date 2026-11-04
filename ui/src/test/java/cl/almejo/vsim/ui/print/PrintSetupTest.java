package cl.almejo.vsim.ui.print;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintSetupTest {

    private static PrintOptions options(Paper paper, PageOrientation orientation) {
        return new PrintOptions(new PrintScope.WholeCircuit(), paper, orientation, new ScaleMode.FitToOnePage(),
                true, false, false, "");
    }

    @Test
    void aSetupMatchesTheSheetsLaidOutOnItsPaperOrientationAndMargin() {
        PrintSetup setup = new PrintSetup(Paper.A4, PageOrientation.PORTRAIT, PageGeometry.CENTIMETRE_POINTS);

        assertThat(setup.matches(options(Paper.A4, PageOrientation.PORTRAIT), 72 / 2.54 + 1e-9)).isTrue();
        assertThat(setup.matches(options(Paper.LETTER, PageOrientation.PORTRAIT), PageGeometry.CENTIMETRE_POINTS))
                .isFalse();
        assertThat(setup.matches(options(Paper.A4, PageOrientation.LANDSCAPE), PageGeometry.CENTIMETRE_POINTS))
                .isFalse();
        assertThat(setup.matches(options(Paper.A4, PageOrientation.PORTRAIT), 40)).isFalse();
    }

    @Test
    void aMarginCannotBeNegative() {
        assertThatThrownBy(() -> new PrintSetup(Paper.A4, PageOrientation.PORTRAIT, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
