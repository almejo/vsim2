package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StartupLanguageTest {

    @Test
    void theCommandLinePropertyWins() {
        assertThat(StartupLanguage.choose("en", Optional.of("es"), Locale.GERMANY)).isEqualTo(Locale.ENGLISH);
        assertThat(StartupLanguage.choose(" es ", Optional.of("en"), Locale.US)).isEqualTo(Locale.forLanguageTag("es"));
    }

    @Test
    void theStoredLanguageComesNext() {
        assertThat(StartupLanguage.choose("", Optional.of("en"), Locale.GERMANY)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void otherwiseTheSystemLocale() {
        assertThat(StartupLanguage.choose("", Optional.empty(), Locale.US)).isEqualTo(Locale.US);
        assertThat(StartupLanguage.choose(null, Optional.empty(), Locale.US)).isEqualTo(Locale.US);
    }

    @Test
    void blankOverridesAreIgnoredAndUnknownLanguagesBecomeSpanish() {
        assertThat(StartupLanguage.choose("   ", Optional.of("en"), Locale.GERMANY)).isEqualTo(Locale.ENGLISH);
        assertThat(StartupLanguage.choose("", Optional.of("  "), Locale.US)).isEqualTo(Locale.US);
        assertThat(Messages.chooseLocale(StartupLanguage.choose("xx", Optional.empty(), Locale.US)))
                .isEqualTo(Messages.SPANISH);
        assertThat(Messages.chooseLocale(StartupLanguage.choose("", Optional.of("fr"), Locale.US)))
                .isEqualTo(Messages.SPANISH);
        assertThat(Messages.chooseLocale(StartupLanguage.choose("not a tag!", Optional.empty(), Locale.US)))
                .isEqualTo(Messages.SPANISH);
    }
}
