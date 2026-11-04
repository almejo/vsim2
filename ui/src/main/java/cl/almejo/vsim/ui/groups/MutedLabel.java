package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.JLabel;
import java.awt.Color;

/**
 * A quiet line («Personalizado», the summary's heading, the unused bits), in {@link ReadableColors#mutedText()} read at
 * paint time so a live theme change shows at once.
 */
@SuppressWarnings("serial")
final class MutedLabel extends JLabel {

    MutedLabel(String text) {
        super(text);
    }

    @Override
    public Color getForeground() {
        return ReadableColors.mutedText();
    }
}
