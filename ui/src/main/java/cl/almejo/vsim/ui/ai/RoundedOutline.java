package cl.almejo.vsim.ui.ai;

import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

/**
 * A thin rounded outline in the theme's border colour, read when painted, with {@code padding} inside it: the AI's
 * question card, «Presentar»'s stage card and panel.
 */
public final class RoundedOutline extends AbstractBorder {

    private static final int ARC = 8;
    private final int padding;

    public RoundedOutline(int padding) {
        this.padding = padding;
    }

    @Override
    public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
        Color color = UIManager.getColor("Component.borderColor");
        graphics.setColor(color != null ? color : Color.GRAY);
        graphics.drawRoundRect(x, y, width - 1, height - 1, ARC, ARC);
    }

    @Override
    public Insets getBorderInsets(Component component) {
        return new Insets(padding, padding, padding, padding);
    }
}
