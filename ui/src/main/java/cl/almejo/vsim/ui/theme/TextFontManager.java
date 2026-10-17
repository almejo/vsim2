package cl.almejo.vsim.ui.theme;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.paint.TextFonts;

/**
 * Applies the text font preference (M6.8): sets {@link TextFonts} at start and whenever a preference changes; the
 * canvases listen to {@link TextFonts} and repaint. Lives as long as the application, like {@link ThemeManager}.
 */
public final class TextFontManager {

    private final AppPreferences preferences;
    private final Runnable preferencesListener = this::apply;
    private boolean started;

    public TextFontManager(AppPreferences preferences) {
        this.preferences = preferences;
    }

    /** Applies the stored font now and follows the preference from then on. Idempotent. */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        TextFont.listFamiliesInBackground();
        apply();
        preferences.addListener(preferencesListener);
    }

    public void dispose() {
        preferences.removeListener(preferencesListener);
        started = false;
    }

    private void apply() {
        TextFonts.set(preferences.textFont());
    }
}
