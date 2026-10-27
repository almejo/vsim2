package cl.almejo.vsim.ui.window.panes;

import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Insets;

/** One pixel in FlatLaf's focus colour around the active pane, read at paint time (the theme changes live). */
final class ActivePaneBorder implements Border {

    private static final Insets NONE = new Insets(0, 0, 0, 0);

    @Override
    public void paintBorder(java.awt.Component component, Graphics graphics, int x, int y, int width,
                            int height) {
        Color color = UIManager.getColor("Component.focusColor");
        graphics.setColor(color != null ? color : new Color(0x1E88E5));
        graphics.drawRect(x, y, width - 1, height - 1);
    }

    @Override
    public Insets getBorderInsets(java.awt.Component component) {
        return (Insets) NONE.clone();
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}
