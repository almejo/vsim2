package cl.almejo.vsim.ui.paint;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    void javasLogicalFamiliesAreInstalledInAnyCase() {
        for (String family : new String[] {"SansSerif", "sansserif", "SERIF", "Monospaced", "Dialog", "dialoginput"}) {
            assertThat(TextFont.isInstalled(family)).as(family).isTrue();
        }
        assertThat(new TextFont("monospaced", 10).labelFont().getFamily())
                .isEqualTo(new Font(Font.MONOSPACED, Font.PLAIN, 10).getFamily());
        assertThat(TextFont.isInstalled("Fuente De Otro Computador")).isFalse();
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
            assertThat(TextFonts.titleFont()).isEqualTo(new TextFont(Font.SERIF, 14).titleFont());
            assertThat(TextFonts.tagFont().getSize()).isEqualTo(13);
        } finally {
            TextFonts.removeListener(listener);
        }
    }

    @Test
    void aFontCanBeLentToOneThreadForAPaintWithoutChangingTheAppsFont() throws Exception {
        TextFonts.set(new TextFont(Font.SERIF, 12));
        AtomicInteger changes = new AtomicInteger();
        Runnable listener = changes::incrementAndGet;
        TextFonts.addListener(listener);
        TextFont frozen = new TextFont(Font.MONOSPACED, 20);
        AtomicReference<TextFont> otherThread = new AtomicReference<>();
        try {
            Font label = TextFonts.withFont(frozen, () -> {
                assertThat(TextFonts.current()).isEqualTo(frozen);
                assertThat(TextFonts.titleFont()).isEqualTo(frozen.titleFont());
                assertThat(TextFonts.tagFont()).isEqualTo(frozen.tagFont());
                CompletableFuture.runAsync(() -> otherThread.set(TextFonts.current())).join();
                TextFont nested = new TextFont(Font.SANS_SERIF, 9);
                TextFonts.runWithFont(nested, () -> assertThat(TextFonts.current()).isEqualTo(nested));
                assertThat(TextFonts.current()).isEqualTo(frozen);
                return TextFonts.labelFont();
            });
            assertThat(label).isEqualTo(frozen.labelFont());
        } finally {
            TextFonts.removeListener(listener);
        }
        assertThat(otherThread.get()).isEqualTo(new TextFont(Font.SERIF, 12));
        assertThat(TextFonts.current()).isEqualTo(new TextFont(Font.SERIF, 12));
        assertThat(TextFonts.labelFont()).isEqualTo(new TextFont(Font.SERIF, 12).labelFont());
        assertThat(changes).hasValue(0);
    }

    @Test
    void aLentFontIsGivenBackWhenThePaintThrows() {
        TextFont frozen = new TextFont(Font.MONOSPACED, 20);
        assertThatThrownBy(() -> TextFonts.runWithFont(frozen, () -> {
            throw new IllegalStateException("paint failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(TextFonts.current()).isEqualTo(TextFont.DEFAULT);
    }
}
