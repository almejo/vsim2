package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.ui.app.AppPreferences;

import java.util.Objects;

/** What the follow mode (M6.7) decides for one move. */
public final class WiresFollow {

    private WiresFollow() {
    }

    /**
     * Whether the wires follow a move made with the given keys down: the follow mode of the preferences, inverted
     * while the configured invert key is held.
     */
    public static boolean followsFor(AppPreferences preferences, boolean shiftDown, boolean altDown) {
        Objects.requireNonNull(preferences, "preferences");
        return preferences.wiresFollow() ^ preferences.invertModifier().isHeld(shiftDown, altDown);
    }

    /**
     * Whether the key that drags a selected wire sideways (spec §8) is held: Alt, or Shift when Alt is the invert
     * key, so the two keys are never the same.
     */
    public static boolean segmentKeyHeld(AppPreferences preferences, boolean shiftDown, boolean altDown) {
        Objects.requireNonNull(preferences, "preferences");
        return preferences.invertModifier() == AppPreferences.InvertModifier.ALT ? shiftDown : altDown;
    }
}
