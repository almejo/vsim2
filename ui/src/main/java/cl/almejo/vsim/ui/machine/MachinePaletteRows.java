package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.theme.ReadableColors;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * The rows of the machine palette, painted as the circuit palette's tree rows are: the icon and the name on one line,
 * the tree's own colours for the chosen row, a soft tint under the pointer, and the name muted when the row is
 * disabled. A {@link ToolRow} is a tool that stays chosen until another is; an {@link ActionRow} runs once. Both are
 * ordinary buttons (the model, the tooltip and the accessible name are the button's), only their look is the row's.
 * Colours are read at every paint, so a theme change shows at once.
 */
final class MachinePaletteRows {

    private static final int VERTICAL_PADDING = 4;
    private static final int HORIZONTAL_PADDING = 6;
    private static final int ICON_GAP = 6;
    private static final int ARC = 8;
    private static final double HOVER_SHARE = 0.5;

    private MachinePaletteRows() {
    }

    /** A tool of the palette: chosen one at a time (the palette's button group). */
    static final class ToolRow extends JToggleButton {

        private static final long serialVersionUID = 1L;

        ToolRow(String text, Icon icon) {
            super(text, icon);
            lookLikeARow(this);
        }

        @Override
        public Dimension getPreferredSize() {
            return preferredSizeOf(this);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            paintRow(graphics, this);
        }
    }

    /** A step of the palette that runs when clicked («Probar…», «Convertir a circuito…»). */
    static final class ActionRow extends JButton {

        private static final long serialVersionUID = 1L;

        ActionRow(String text, Icon icon) {
            super(text, icon);
            lookLikeARow(this);
        }

        @Override
        public Dimension getPreferredSize() {
            return preferredSizeOf(this);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            paintRow(graphics, this);
        }
    }

    private static void lookLikeARow(AbstractButton row) {
        row.setContentAreaFilled(false);
        row.setBorderPainted(false);
        row.setFocusPainted(false);
        row.setOpaque(false);
        row.setRolloverEnabled(true);
        row.setHorizontalAlignment(SwingConstants.LEADING);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static Dimension preferredSizeOf(AbstractButton row) {
        FontMetrics metrics = row.getFontMetrics(row.getFont());
        Icon icon = row.getIcon();
        int iconHeight = icon == null ? 0 : icon.getIconHeight();
        int iconWidth = icon == null ? 0 : icon.getIconWidth() + UIScale.scale(ICON_GAP);
        int height = Math.max(iconHeight, metrics.getHeight()) + UIScale.scale(VERTICAL_PADDING);
        int width = UIScale.scale(2 * HORIZONTAL_PADDING) + iconWidth + metrics.stringWidth(row.getText());
        return new Dimension(width, height);
    }

    private static void paintRow(Graphics graphics, AbstractButton row) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean chosen = row.isSelected() && row.isEnabled();
            Color background = backgroundOf(row, chosen);
            if (background != null) {
                canvas.setColor(background);
                canvas.fill(new RoundRectangle2D.Double(0, 0, row.getWidth(), row.getHeight(),
                        UIScale.scale(ARC), UIScale.scale(ARC)));
            }
            int left = UIScale.scale(HORIZONTAL_PADDING);
            Icon icon = row.getIcon();
            if (icon != null) {
                icon.paintIcon(row, canvas, left, (row.getHeight() - icon.getIconHeight()) / 2);
                left += icon.getIconWidth() + UIScale.scale(ICON_GAP);
            }
            canvas.setFont(row.getFont());
            canvas.setColor(textColorOf(row, chosen));
            FontMetrics metrics = canvas.getFontMetrics();
            canvas.drawString(row.getText(), left,
                    (row.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            if (row.isFocusOwner()) {
                Color focus = UIManager.getColor("Component.focusColor");
                if (focus != null) {
                    canvas.setColor(focus);
                    canvas.draw(new RoundRectangle2D.Double(0.5, 0.5, row.getWidth() - 1, row.getHeight() - 1,
                            UIScale.scale(ARC), UIScale.scale(ARC)));
                }
            }
        } finally {
            canvas.dispose();
        }
    }

    /** The tree's selection for the chosen row, a tint of it under the pointer, else none (the panel shows). */
    private static Color backgroundOf(AbstractButton row, boolean chosen) {
        Color selection = UIManager.getColor("Tree.selectionBackground");
        if (selection == null) {
            selection = UIManager.getColor("List.selectionBackground");
        }
        if (selection == null) {
            return null;
        }
        if (chosen) {
            return selection;
        }
        if (row.isEnabled() && row.getModel().isRollover()) {
            Color panel = ReadableColors.panelBackground();
            return new Color(blend(panel.getRed(), selection.getRed()), blend(panel.getGreen(), selection.getGreen()),
                    blend(panel.getBlue(), selection.getBlue()));
        }
        return null;
    }

    private static int blend(int from, int towards) {
        return (int) Math.round(from + (towards - from) * HOVER_SHARE);
    }

    private static Color textColorOf(AbstractButton row, boolean chosen) {
        if (!row.isEnabled()) {
            return ReadableColors.mutedText();
        }
        Color text = UIManager.getColor(chosen ? "Tree.selectionForeground" : "Tree.textForeground");
        if (text == null) {
            text = UIManager.getColor("Label.foreground");
        }
        return text == null ? row.getForeground() : text;
    }
}
