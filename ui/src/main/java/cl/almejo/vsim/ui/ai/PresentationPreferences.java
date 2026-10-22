package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.ui.app.AppPreferences;

import java.time.Duration;
import java.util.Objects;

/** A window's «Presentar» mode as its preferences hold it (IA.2, spec §4, §5): on/off, pace and pause. */
public final class PresentationPreferences {

    private PresentationPreferences() {
    }

    /** The presentation settings {@code preferences} hold now. */
    public static PresentationSettings read(AppPreferences preferences) {
        Objects.requireNonNull(preferences, "preferences");
        return new PresentationSettings(preferences.aiPresenting(), preferences.aiPace(),
                Duration.ofMillis(preferences.aiPauseMillis()));
    }
}
