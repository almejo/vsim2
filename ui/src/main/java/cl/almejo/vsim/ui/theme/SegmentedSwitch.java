package cl.almejo.vsim.ui.theme;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Two or more choices as one segmented control, as «De corrido» | «Paso a paso» in «Presentar» and «Solo esta pieza» |
 * «Todas las piezas» in «Cambiar forma» (the user's first look at the pace: which one is on was not obvious): the
 * chosen segment filled with the theme's accent and its text in a colour readable on it, the other one plain,
 * inside one rounded outline. Colours read from the theme at each paint (light and dark change live). EDT only.
 */
public final class SegmentedSwitch extends JPanel {

    private static final int ARC = 8;
    private final ButtonGroup group = new ButtonGroup();

    public SegmentedSwitch(JToggleButton... segments) {
        super(new GridLayout(1, segments.length, 2, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        for (JToggleButton segment : segments) {
            group.add(segment);
            add(segment);
        }
    }

    /** The accent the chosen segment is filled with. */
    public static Color accent() {
        Color accent = UIManager.getColor("Component.accentColor");
        if (accent == null) {
            accent = UIManager.getColor("Component.focusColor");
        }
        if (accent == null) {
            accent = UIManager.getColor("List.selectionBackground");
        }
        return accent != null ? accent : new Color(0x2675BF);
    }

    /** White or black, whichever reads better on the accent. */
    public static Color onAccent() {
        Color accent = accent();
        return ReadableColors.contrast(Color.WHITE, accent) >= ReadableColors.contrast(Color.BLACK, accent)
                ? Color.WHITE : Color.BLACK;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D outline = (Graphics2D) graphics.create();
        try {
            outline.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color border = UIManager.getColor("Component.borderColor");
            outline.setColor(border != null ? border : Color.GRAY);
            outline.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, ARC + 2,
                    ARC + 2));
        } finally {
            outline.dispose();
        }
    }

    /** One segment: no button chrome of its own; the chosen one is a filled rounded rectangle. */
    public static final class Segment extends JToggleButton {

        public Segment(String text) {
            super(text);
            putClientProperty("html.disable", Boolean.TRUE);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setFocusPainted(true);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }

        @Override
        public Color getForeground() {
            if (isSelected()) {
                return onAccent();
            }
            Color text = UIManager.getColor("Label.foreground");
            return text != null ? text : super.getForeground();
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, super.getMinimumSize().height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (isSelected() || getModel().isRollover()) {
                Graphics2D fill = (Graphics2D) graphics.create();
                try {
                    fill.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color accent = accent();
                    fill.setColor(isSelected() ? accent
                            : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
                    fill.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), ARC, ARC));
                } finally {
                    fill.dispose();
                }
            }
            super.paintComponent(graphics);
        }
    }
}
