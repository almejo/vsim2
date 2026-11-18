package cl.almejo.vsim.ui.machine;

import java.awt.Color;
import java.awt.Cursor;
import java.io.Serial;
import javax.swing.JButton;
import javax.swing.UIManager;

/**
 * A link in the machine's Propiedades («en 4 flechas», a problem's «Ver»): a borderless button in the theme's link
 * colour (read at paint time), with a hand cursor. One class so every link looks and behaves the same.
 */
final class MachineLinkButton extends JButton {
    @Serial
    private static final long serialVersionUID = 1L;

    MachineLinkButton(String text) {
        super(text);
        putClientProperty("JButton.buttonType", "borderless");
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Color getForeground() {
        Color colour = UIManager.getColor("Component.linkColor");
        return colour != null ? colour : super.getForeground();
    }
}
