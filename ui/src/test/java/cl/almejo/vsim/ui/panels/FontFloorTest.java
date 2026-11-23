package cl.almejo.vsim.ui.panels;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Font;

import static org.assertj.core.api.Assertions.assertThat;

/** Fonts derived smaller keep 9 px or more only while the interface size is under 100 %; from 100 % up nothing changes. */
class FontFloorTest {

    private static final Font TEN_PIXELS = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

    @AfterEach
    void standardSize() {
        FontFloor.useInterfaceZoom(100);
    }

    @Test
    void at100PercentASmallSystemFontShrinksAsAsked() {
        FontFloor.useInterfaceZoom(100);
        assertThat(FontFloor.scaled(TEN_PIXELS, 0.8f).getSize2D()).isEqualTo(8f);
        assertThat(FontFloor.smaller(TEN_PIXELS, 2f).getSize2D()).isEqualTo(8f);
        FontFloor.useInterfaceZoom(150);
        assertThat(FontFloor.scaled(TEN_PIXELS, Font.BOLD, 0.8f).getSize2D()).isEqualTo(8f);
    }

    @Test
    void under100PercentTheFloorIsNinePixels() {
        FontFloor.useInterfaceZoom(75);
        assertThat(FontFloor.scaled(TEN_PIXELS, 0.8f).getSize2D()).isEqualTo(9f);
        assertThat(FontFloor.smaller(TEN_PIXELS, 2f).getSize2D()).isEqualTo(9f);
        assertThat(FontFloor.scaled(TEN_PIXELS, Font.BOLD, 0.95f).getSize2D()).isEqualTo(9.5f);
    }

    @Test
    void theFloorNeverMakesAFontBiggerThanItsBase() {
        FontFloor.useInterfaceZoom(75);
        Font eightPixels = TEN_PIXELS.deriveFont(8f);
        assertThat(FontFloor.scaled(eightPixels, 0.8f).getSize2D()).isEqualTo(8f);
    }
}
