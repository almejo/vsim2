package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.JLabel;
import java.awt.Color;

/**
 * A label of the shape editor whose text colour follows its {@link Tone}, read from the theme at each paint (a
 * {@code setForeground} would keep the old theme's colour after a live change).
 */
@SuppressWarnings("serial")
final class ToneLabel extends JLabel {
    /** What a line says: plain text, a hint («ⓘ»), a warning that does not block, or what blocks «Aceptar». */
    enum Tone { PLAIN, INFO, WARNING, BLOCKING }

    private Tone tone;

    ToneLabel(Tone tone) {
        this.tone = tone;
        putClientProperty("html.disable", Boolean.TRUE);
    }

    Tone tone() {
        return tone;
    }

    /** The tone and its icon: a hint gets ⓘ, a warning ⚠, a blocking problem ⛔ (never colour alone). */
    void show(Tone shown, String text) {
        tone = shown;
        setText(text);
        setIcon(ProblemIcon.of(shown).orElse(null));
        repaint();
    }

    @Override
    public Color getForeground() {
        return colorOf(tone, super.getForeground());
    }

    /** The colour of a tone, read from the theme now; {@code plain} is used for no tone or {@link Tone#PLAIN}. */
    static Color colorOf(Tone tone, Color plain) {
        if (tone == null) {
            return plain;
        }
        return switch (tone) {
            case PLAIN -> plain;
            case INFO -> ReadableColors.mutedText();
            case WARNING -> ReadableColors.warningText();
            case BLOCKING -> ReadableColors.errorText();
        };
    }
}
