package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.wiring.InvertModifierNames;
import cl.almejo.vsim.ui.wiring.WiresFollow;

import java.util.ArrayList;
import java.util.List;

/**
 * The texts of {@link Tool#gesturePhrase}: one line per gesture saying what the next click and the keys that act right
 * now do. Fixed keys (Esc, Retroceso, Espacio, Enter) are bundle texts, not {@code KeyEvent.getKeyText}, whose names
 * follow the JVM's language; the keys the user can change (the invert key of a move) are named like the preferences
 * name them.
 */
final class GesturePhrases {

    private GesturePhrases() {
    }

    /** A wire drawn between clicks: corners, Espacio, Retroceso. */
    static String continuousWire() {
        return Messages.text("gesture.wireContinuous");
    }

    /** An «L» dragged with the button down: no corners, and Retroceso does nothing there. */
    static String pieceWire() {
        return Messages.text("gesture.wirePieces");
    }

    /**
     * A selection being dragged: the invert key says what it does to the wires (nothing when no key inverts), Ctrl at
     * the release copies, and a single wire slides sideways with its own key.
     */
    static String moving(AppPreferences preferences, boolean slidingAWire) {
        List<String> parts = new ArrayList<>();
        parts.add(Messages.text("gesture.moving"));
        AppPreferences.InvertModifier invertKey = preferences.invertModifier();
        if (invertKey != AppPreferences.InvertModifier.NONE) {
            parts.add(Messages.format(preferences.wiresFollow() ? "gesture.invertWiresStay" : "gesture.invertWiresFollow",
                    InvertModifierNames.of(invertKey)));
        }
        parts.add(Messages.text("gesture.copyOnRelease"));
        if (slidingAWire) {
            parts.add(Messages.format("gesture.slideWire", InvertModifierNames.of(WiresFollow.segmentKey(preferences))));
        }
        parts.add(Messages.text("gesture.cancel"));
        return String.join(Messages.text("gesture.separator"), parts);
    }

    /** A copied fragment following the pointer. */
    static String pasting() {
        return Messages.text("gesture.pasting");
    }

    /** A fragment dragged out of the palette (a user component) following the pointer. */
    static String dropping(String componentName) {
        return Messages.format("gesture.dropping", componentName);
    }

    /** A part from the palette following the pointer. */
    static String placing(String componentName) {
        return Messages.format("gesture.placing", componentName);
    }
}
