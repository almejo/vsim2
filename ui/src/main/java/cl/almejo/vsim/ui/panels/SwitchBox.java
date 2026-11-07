package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ThemeColors;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.JCheckBox;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * An on/off setting drawn as a switch (a track with a knob) followed by «Sí» or «No». It is a {@link JCheckBox}, so
 * everything that reads or listens to a check box (the value, the action listeners, Space) works unchanged. Colours
 * come from the look and feel every time it paints.
 */
final class SwitchBox extends JCheckBox {

    private static final int TRACK_WIDTH = 30;
    private static final int TRACK_HEIGHT = 16;
    private static final int KNOB_MARGIN = 2;
    private static final int GAP = 8;

    SwitchBox(boolean selected) {
        super("", selected);
        setOpaque(false);
        setFocusPainted(false);
        addItemListener(event -> showState());
        showState();
    }

    private void showState() {
        setText(Messages.text(isSelected() ? "properties.switch.on" : "properties.switch.off"));
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        showState();
    }

    private static int trackWidth() {
        return UIScale.scale(TRACK_WIDTH);
    }

    private static int trackHeight() {
        return UIScale.scale(TRACK_HEIGHT);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int widest = Math.max(metrics.stringWidth(Messages.text("properties.switch.on")),
                metrics.stringWidth(Messages.text("properties.switch.off")));
        return new Dimension(trackWidth() + UIScale.scale(GAP) + widest + 2,
                Math.max(trackHeight() + UIScale.scale(4), metrics.getHeight() + 2));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int trackWidth = trackWidth();
            int trackHeight = trackHeight();
            int knobMargin = UIScale.scale(KNOB_MARGIN);
            int top = (getHeight() - trackHeight) / 2;
            boolean on = isSelected();
            Color track = on ? ThemeColors.accent()
                    : ThemeColors.ui("Component.borderColor", Color.GRAY);
            if (!isEnabled()) {
                track = ThemeColors.ui("Component.disabledBorderColor", track);
            }
            local.setColor(track);
            local.fillRoundRect(0, top, trackWidth, trackHeight, trackHeight, trackHeight);
            if (hasFocus()) {
                local.setColor(ThemeColors.ui("Component.focusColor", track));
                local.drawRoundRect(-1, top - 1, trackWidth + 1, trackHeight + 1, trackHeight + 2, trackHeight + 2);
            }
            int knobSize = trackHeight - 2 * knobMargin;
            int knobX = on ? trackWidth - knobMargin - knobSize : knobMargin;
            local.setColor(ThemeColors.ui("Panel.background", Color.WHITE).brighter());
            local.fillOval(knobX, top + knobMargin, knobSize, knobSize);
            local.setFont(getFont());
            FontMetrics metrics = local.getFontMetrics();
            local.setColor(isEnabled() ? getForeground() : ThemeColors.ui("Label.disabledForeground", Color.GRAY));
            local.drawString(getText(), trackWidth + UIScale.scale(GAP), (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally {
            local.dispose();
        }
    }

}
