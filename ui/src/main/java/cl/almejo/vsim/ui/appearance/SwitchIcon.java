package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.theme.SegmentedSwitch;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws a check box as an on/off switch («Mostrar nombres de pines» in the mockups): a rounded track in the theme's
 * accent when on, grey when off, with a white knob at the right or the left. Colours read at each paint.
 */
final class SwitchIcon implements Icon {
    private static final int WIDTH = 30;
    private static final int HEIGHT = 16;
    private static final int KNOB_MARGIN = 2;
    private static final float DISABLED_ALPHA = 0.45f;

    @Override
    public int getIconWidth() {
        return WIDTH;
    }

    @Override
    public int getIconHeight() {
        return HEIGHT;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        boolean on = component instanceof AbstractButton button && button.isSelected();
        Graphics2D track = (Graphics2D) graphics.create();
        try {
            track.translate(x, y);
            track.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!component.isEnabled()) {
                track.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DISABLED_ALPHA));
            }
            track.setColor(on ? SegmentedSwitch.accent() : offTrack());
            track.fill(new RoundRectangle2D.Double(0, 0, WIDTH, HEIGHT, HEIGHT, HEIGHT));
            int knob = HEIGHT - 2 * KNOB_MARGIN;
            int knobX = on ? WIDTH - KNOB_MARGIN - knob : KNOB_MARGIN;
            track.setColor(Color.WHITE);
            track.fill(new Ellipse2D.Double(knobX, KNOB_MARGIN, knob, knob));
        } finally {
            track.dispose();
        }
    }

    private static Color offTrack() {
        Color border = UIManager.getColor("Component.borderColor");
        Color text = UIManager.getColor("Label.disabledForeground");
        return text != null ? text : border != null ? border : Color.GRAY;
    }
}
