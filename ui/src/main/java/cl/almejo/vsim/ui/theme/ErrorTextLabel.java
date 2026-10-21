package cl.almejo.vsim.ui.theme;

import javax.swing.JLabel;
import java.awt.Color;

/**
 * A label for an error text, always in {@link ReadableColors#errorText()} read at paint time, so a live theme change
 * shows at once (a {@code setForeground} would keep the old theme's red).
 */
public final class ErrorTextLabel extends JLabel {

    public ErrorTextLabel() {
        this("");
    }

    public ErrorTextLabel(String text) {
        super(text);
    }

    @Override
    public Color getForeground() {
        return ReadableColors.errorText();
    }
}
