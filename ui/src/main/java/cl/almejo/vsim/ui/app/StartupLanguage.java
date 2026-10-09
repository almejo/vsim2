package cl.almejo.vsim.ui.app;

import java.util.Locale;
import java.util.Optional;

/** The language VSim starts in (spec §6.10): {@code -Dvsim.language}, then the stored preference, then the system. */
final class StartupLanguage {

    private StartupLanguage() {
    }

    static Locale choose(String override, Optional<String> stored, Locale system) {
        if (override != null && !override.isBlank()) {
            return Locale.forLanguageTag(override.strip());
        }
        return stored.filter(tag -> !tag.isBlank())
                .map(tag -> Locale.forLanguageTag(tag.strip()))
                .orElse(system);
    }
}
