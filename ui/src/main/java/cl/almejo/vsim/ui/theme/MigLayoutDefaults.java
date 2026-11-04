package cl.almejo.vsim.ui.theme;

import net.miginfocom.swing.SwingComponentWrapper;

/**
 * MigLayout's global settings for VSim's look. MigLayout's visual padding moves combo boxes, spinners and tabbed
 * panes by the size of the borders of the native macOS (Aqua) and Windows components so their visible edges line up;
 * VSim draws FlatLaf's components on every system, whose edges are their bounds, so the padding is turned off and a
 * form's fields line up on every system.
 */
public final class MigLayoutDefaults {

    private MigLayoutDefaults() {
    }

    /** Sets them; called once at startup, before any window is built. */
    public static void install() {
        SwingComponentWrapper.setVisualPaddingEnabled(false);
    }
}
