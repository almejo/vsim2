package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JCheckBox;
import javax.swing.UIManager;
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

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int widest = Math.max(metrics.stringWidth(Messages.text("properties.switch.on")),
                metrics.stringWidth(Messages.text("properties.switch.off")));
        return new Dimension(TRACK_WIDTH + GAP + widest + 2, Math.max(TRACK_HEIGHT + 4, metrics.getHeight() + 2));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int top = (getHeight() - TRACK_HEIGHT) / 2;
            boolean on = isSelected();
            Color track = on ? color("Component.accentColor", new Color(0x2F6FDE))
                    : color("Component.borderColor", Color.GRAY);
            if (!isEnabled()) {
                track = color("Component.disabledBorderColor", track);
            }
            local.setColor(track);
            local.fillRoundRect(0, top, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
            if (hasFocus()) {
                local.setColor(color("Component.focusColor", track));
                local.drawRoundRect(-1, top - 1, TRACK_WIDTH + 1, TRACK_HEIGHT + 1, TRACK_HEIGHT + 2, TRACK_HEIGHT + 2);
            }
            int knobSize = TRACK_HEIGHT - 2 * KNOB_MARGIN;
            int knobX = on ? TRACK_WIDTH - KNOB_MARGIN - knobSize : KNOB_MARGIN;
            local.setColor(color("Panel.background", Color.WHITE).brighter());
            local.fillOval(knobX, top + KNOB_MARGIN, knobSize, knobSize);
            local.setFont(getFont());
            FontMetrics metrics = local.getFontMetrics();
            local.setColor(isEnabled() ? getForeground() : color("Label.disabledForeground", Color.GRAY));
            local.drawString(getText(), TRACK_WIDTH + GAP, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally {
            local.dispose();
        }
    }

    private static Color color(String key, Color fallback) {
        Color found = UIManager.getColor(key);
        return found != null ? found : fallback;
    }
}
