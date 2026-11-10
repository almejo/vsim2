package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;

/**
 * How the keys of the follow mode are called wherever the user reads them (the preferences form, the tooltip of the
 * follow toggle, the status bar while moving): one place, so a key never has two names.
 */
public final class InvertModifierNames {

    private InvertModifierNames() {
    }

    /** The text of an invert key («Mayús», «Alt», «Ninguna»). */
    public static String of(AppPreferences.InvertModifier modifier) {
        return switch (modifier) {
            case SHIFT -> Messages.text("preferences.invertModifier.SHIFT");
            case ALT -> Messages.text("preferences.invertModifier.ALT");
            case NONE -> Messages.text("preferences.invertModifier.NONE");
        };
    }
}
