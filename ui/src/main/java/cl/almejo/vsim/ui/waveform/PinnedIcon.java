package cl.almejo.vsim.ui.waveform;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

/**
 * The pressed pin of the waveform toolbar: {@code waveform-pinned.svg}, an upright filled pushpin, painted in the
 * theme's red ({@code Actions.Red}, FlatLaf's red for light and dark themes) so a pinned panel stands out. The colour is
 * read at every paint, never kept (the theme changes live).
 */
final class PinnedIcon implements Icon {

    /** FlatLaf's light {@code Actions.Red}, for a look and feel that does not define it. */
    private static final Color FALLBACK_RED = new Color(0xDB5860);

    private final FlatSVGIcon pin;

    PinnedIcon(int size) {
        pin = new FlatSVGIcon("icons/" + WaveformIcons.PINNED + ".svg", size, size, PinnedIcon.class.getClassLoader());
        pin.setColorFilter(new FlatSVGIcon.ColorFilter(drawn -> red()));
    }

    /** The red the pin is painted in now. */
    static Color red() {
        Color red = UIManager.getColor("Actions.Red");
        return red != null ? red : FALLBACK_RED;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        pin.paintIcon(component, graphics, x, y);
    }

    @Override
    public int getIconWidth() {
        return pin.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return pin.getIconHeight();
    }
}
