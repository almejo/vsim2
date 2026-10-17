package cl.almejo.vsim.ui.theme;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.paint.TextFonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Font;

import static org.assertj.core.api.Assertions.assertThat;

class TextFontManagerTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final TextFontManager manager = new TextFontManager(preferences);

    @AfterEach
    void restore() {
        manager.dispose();
        TextFonts.set(TextFont.DEFAULT);
    }

    @Test
    void startAppliesTheStoredFontAndFollowsEveryChangeUntilDisposed() {
        preferences.setTextFont(new TextFont(Font.SERIF, 12));
        assertThat(TextFonts.current()).isEqualTo(TextFont.DEFAULT);

        manager.start();
        assertThat(TextFonts.current()).isEqualTo(new TextFont(Font.SERIF, 12));
        assertThat(preferences.listenerCount()).isEqualTo(1);

        preferences.setTextFont(new TextFont(Font.SERIF, 16));
        assertThat(TextFonts.current()).isEqualTo(new TextFont(Font.SERIF, 16));

        manager.dispose();
        assertThat(preferences.listenerCount()).isZero();
        preferences.setTextFont(new TextFont(Font.SERIF, 18));
        assertThat(TextFonts.current()).isEqualTo(new TextFont(Font.SERIF, 16));
    }
}
