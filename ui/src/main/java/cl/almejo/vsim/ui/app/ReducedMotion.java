package cl.almejo.vsim.ui.app;

import java.awt.AWTError;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.util.function.Function;

/**
 * Whether the desktop asks applications for less motion. Java exposes it only on Linux desktops that publish GTK's
 * XSETTINGS (GNOME's «Animaciones» switch, {@code gtk-enable-animations}); elsewhere the answer is «no».
 */
final class ReducedMotion {

    static final String GTK_ENABLE_ANIMATIONS = "gnome.Gtk/EnableAnimations";

    private ReducedMotion() {
    }

    static boolean requested() {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        try {
            return requested(Toolkit.getDefaultToolkit()::getDesktopProperty);
        } catch (RuntimeException | AWTError exception) {
            // No toolkit or no such property here: animate as usual.
            return false;
        }
    }

    /** From a desktop property reader: GTK's switch is an integer (0 = off) on X11 and a boolean in some toolkits. */
    static boolean requested(Function<String, Object> desktopProperty) {
        Object animations = desktopProperty.apply(GTK_ENABLE_ANIMATIONS);
        if (animations instanceof Number number) {
            return number.intValue() == 0;
        }
        if (animations instanceof Boolean enabled) {
            return !enabled;
        }
        return false;
    }
}
