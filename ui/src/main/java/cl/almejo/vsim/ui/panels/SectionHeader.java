package cl.almejo.vsim.ui.panels;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;

/**
 * The header of a section of Propiedades: its name in small bold capitals, then a thin rule to the right edge. The
 * font and the colours are read from the look and feel every time it paints, so a theme switch never leaves it stale.
 */
final class SectionHeader extends JComponent {

    private static final float FONT_FACTOR = 0.85f;
    private static final int GAP = 6;
    private static final int VERTICAL_PADDING = 2;

    private final String text;

    SectionHeader(String text) {
        this.text = text;
        setOpaque(false);
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.LABEL;
                }
            };
            accessibleContext.setAccessibleName(text);
        }
        return accessibleContext;
    }

    /** The name as given (the capitals are only how it is painted). */
    String text() {
        return text;
    }

    private Font headerFont() {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = getFont();
        }
        return base.deriveFont(Font.BOLD, base.getSize2D() * FONT_FACTOR);
    }

    private String shownText() {
        return text.toUpperCase(Locale.ROOT);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(headerFont());
        return new Dimension(metrics.stringWidth(shownText()) + UIScale.scale(GAP),
                metrics.getHeight() + UIScale.scale(2 * VERTICAL_PADDING));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = headerFont();
            local.setFont(font);
            FontMetrics metrics = local.getFontMetrics();
            Color muted = UIManager.getColor("Label.disabledForeground");
            Color rule = UIManager.getColor("Component.borderColor");
            if (rule == null) {
                rule = UIManager.getColor("Separator.foreground");
            }
            local.setColor(muted != null ? muted : Color.GRAY);
            int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            local.drawString(shownText(), 0, baseline);
            int ruleStart = metrics.stringWidth(shownText()) + UIScale.scale(GAP);
            if (ruleStart < getWidth()) {
                local.setColor(rule != null ? rule : Color.LIGHT_GRAY);
                int ruleY = getHeight() / 2;
                local.drawLine(ruleStart, ruleY, getWidth() - 1, ruleY);
            }
        } finally {
            local.dispose();
        }
    }
}
