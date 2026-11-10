package cl.almejo.vsim.ui.whatsnew;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.JButton;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A button of a bubble: outlined in the bubble's ink («Listo», ‹), or solid ink with the bubble's colour for its text
 * (›). It never takes the keyboard (the canvas keeps it; the arrows and Enter walk the bubbles), so using it is never
 * «starting to edit». Colours read at paint time.
 */
final class BubbleButton extends JButton {

    /** How the button is drawn. */
    enum Look { OUTLINE, SOLID }

    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 4;
    private static final int CORNER = 10;

    private final Look look;

    BubbleButton(String text, Look look, Runnable action) {
        super(text);
        this.look = look;
        setFocusable(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        addActionListener(event -> action.run());
    }

    @Override
    public Font getFont() {
        Font font = UIManager.getFont("Button.font");
        return font == null ? super.getFont() : font.deriveFont(Font.BOLD);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        return new Dimension(metrics.stringWidth(getText()) + 2 * UIScale.scale(PADDING_X),
                metrics.getHeight() + 2 * UIScale.scale(PADDING_Y));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            painter.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color ink = isEnabled() ? BubbleColors.ink() : BubbleColors.disabledInk();
            int corner = UIScale.scale(CORNER);
            if (look == Look.SOLID) {
                painter.setColor(ink);
                painter.fillRoundRect(0, 0, getWidth(), getHeight(), corner, corner);
            } else {
                painter.setColor(ink);
                painter.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, corner, corner);
            }
            if (getModel().isRollover() && isEnabled()) {
                painter.setColor(BubbleColors.rule());
                painter.fillRoundRect(0, 0, getWidth(), getHeight(), corner, corner);
            }
            painter.setFont(getFont());
            painter.setColor(look == Look.SOLID ? BubbleColors.fill() : ink);
            FontMetrics metrics = painter.getFontMetrics();
            painter.drawString(getText(), (getWidth() - metrics.stringWidth(getText())) / 2,
                    (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally {
            painter.dispose();
        }
    }
}
