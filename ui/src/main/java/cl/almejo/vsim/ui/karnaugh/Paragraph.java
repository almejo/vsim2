package cl.almejo.vsim.ui.karnaugh;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.Font;

/** Read-only text that wraps at the width it is given and looks like a label (follows the theme on updateUI). */
final class Paragraph extends JTextArea {

    private final int style;

    Paragraph(String text) {
        this(text, Font.PLAIN);
    }

    /** {@code style} is a {@link Font} style ({@code Font.BOLD}), kept across theme changes. */
    Paragraph(String text, int style) {
        super(text);
        this.style = style;
        setEditable(false);
        setFocusable(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());
        applyLabelLook();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyLabelLook();
    }

    private void applyLabelLook() {
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            setFont(font.deriveFont(style));
        }
        if (UIManager.getColor("Label.foreground") != null) {
            setForeground(UIManager.getColor("Label.foreground"));
        }
    }
}
