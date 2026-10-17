package cl.almejo.vsim.ui.paint;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextFontTest {

    @AfterEach
    void restore() {
        TextFonts.set(TextFont.DEFAULT);
    }

    @Test
    void theDefaultIsSansSerifTenPlainWithBoldElevenTitles() {
        assertThat(TextFont.DEFAULT).isEqualTo(new TextFont(Font.SANS_SERIF, 10));
        Font label = TextFont.DEFAULT.labelFont();
        assertThat(label.getFamily()).isEqualTo(new Font(Font.SANS_SERIF, Font.PLAIN, 10).getFamily());
        assertThat(label.getStyle()).isEqualTo(Font.PLAIN);
        assertThat(label.getSize()).isEqualTo(10);
        Font title = TextFont.DEFAULT.titleFont();
        assertThat(title.getStyle()).isEqualTo(Font.BOLD);
        assertThat(title.getSize()).isEqualTo(11);
    }

    @Test
    void aFamilyThatIsNotInstalledPaintsInSansSerifAndKeepsItsName() {
        TextFont missing = new TextFont("Fuente De Otro Computador", 12);
        assertThat(missing.family()).isEqualTo("Fuente De Otro Computador");
        assertThat(missing.labelFont().getFamily()).isEqualTo(new Font(Font.SANS_SERIF, Font.PLAIN, 12).getFamily());
        assertThat(missing.titleFont().getFamily()).isEqualTo(new Font(Font.SANS_SERIF, Font.BOLD, 13).getFamily());
        assertThat(new TextFont(Font.SERIF, 12).labelFont().getFamily()).isEqualTo(Font.SERIF);
    }

    @Test
    void theSizeGoesFromEightToTwentyFour() {
        assertThat(new TextFont(Font.SERIF, 8).size()).isEqualTo(8);
        assertThat(new TextFont(Font.SERIF, 24).size()).isEqualTo(24);
        assertThatThrownBy(() -> new TextFont(Font.SERIF, 7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextFont(Font.SERIF, 25)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCurrentFontChangesTheDerivedFontsAndNotifiesOnlyRealChanges() {
        AtomicInteger changes = new AtomicInteger();
        Runnable listener = changes::incrementAndGet;
        TextFonts.addListener(listener);
        try {
            TextFonts.set(TextFont.DEFAULT);
            assertThat(changes).hasValue(0);
            TextFonts.set(new TextFont(Font.SERIF, 14));
            assertThat(changes).hasValue(1);
            assertThat(TextFonts.current()).isEqualTo(new TextFont(Font.SERIF, 14));
            assertThat(TextFonts.labelFont().getFamily()).isEqualTo(Font.SERIF);
            assertThat(TextFonts.labelFont().getSize()).isEqualTo(14);
            assertThat(TextFonts.titleFont().getSize()).isEqualTo(15);
            assertThat(TextFonts.tagFont().getSize()).isEqualTo(13);
        } finally {
            TextFonts.removeListener(listener);
        }
    }
}
