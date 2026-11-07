package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.icons.Icons;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.util.Map;
import java.util.Optional;

/**
 * The small grey icon before the caption of a common setting in the Propiedades panel. A setting without one gets an
 * empty icon of the same size, so the captions stay aligned.
 */
public final class ParameterIcons {

    /** The side of a caption icon, in pixels before UI scaling. */
    static final int SIZE = 14;

    /** Parameter name to icon resource (without folder and extension). */
    private static final Map<String, String> ICONS = Map.ofEntries(
            Map.entry("label", "parameter-label"),
            Map.entry("text", "parameter-text"),
            Map.entry("name", "parameter-name"),
            Map.entry("inputs", "parameter-inputs"),
            Map.entry("outputs", "parameter-outputs"),
            Map.entry("width", "parameter-width"),
            Map.entry("inputBits", "parameter-input-bits"),
            Map.entry("delay", "parameter-delay"),
            Map.entry("time-up", "parameter-time-up"),
            Map.entry("time-down", "parameter-time-down"),
            Map.entry("negate", "parameter-negate"),
            Map.entry("color", "parameter-color"),
            Map.entry("value", "parameter-value"),
            Map.entry("format", "parameter-format"),
            Map.entry("direction", "parameter-direction"),
            Map.entry("words", "parameter-words"),
            Map.entry("contents", "parameter-contents"));

    private static final Icon EMPTY = new Icon() {
        @Override
        public void paintIcon(Component owner, Graphics graphics, int x, int y) {
        }

        // the real icons report their UI-scaled size: the empty one too, or the captions would not line up
        @Override
        public int getIconWidth() {
            return UIScale.scale(SIZE);
        }

        @Override
        public int getIconHeight() {
            return UIScale.scale(SIZE);
        }
    };

    private ParameterIcons() {
    }

    /** Every icon resource this class uses, sorted (a test checks each icon file has a user). */
    public static java.util.Collection<String> names() {
        return new java.util.TreeSet<>(ICONS.values());
    }

    /** The icon resource name of {@code parameterName}; empty for a setting without one. */
    static Optional<String> iconName(String parameterName) {
        return Optional.ofNullable(ICONS.get(parameterName));
    }

    /** The 14 px icon of {@code parameterName}, or an empty icon of the same size. */
    static Icon labelIcon(String parameterName) {
        return iconName(parameterName).<Icon>map(name -> Icons.get(name, SIZE)).orElse(EMPTY);
    }
}
