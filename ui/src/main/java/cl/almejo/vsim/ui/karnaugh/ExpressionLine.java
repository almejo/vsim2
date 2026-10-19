package cl.almejo.vsim.ui.karnaugh;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * One minimized expression, «F = B'C + BD», with each term painted in its group's colour (read at paint time, so a
 * theme change recolours it) and the rest in the theme's text colour.
 */
final class ExpressionLine extends JComponent {

    /** A piece of the text: a term with its palette colour, or plain text ({@code colorIndex} -1). */
    record Run(String text, int colorIndex) {
    }

    private static final float SIZE_FACTOR = 1.5f;
    private static final int PADDING = 4;

    private final List<Run> runs;

    ExpressionLine(List<Run> runs) {
        this.runs = List.copyOf(runs);
    }

    List<Run> runs() {
        return runs;
    }

    String text() {
        StringBuilder text = new StringBuilder();
        runs.forEach(run -> text.append(run.text()));
        return text.toString();
    }

    private Font expressionFont() {
        Font base = getFont() != null ? getFont() : new Font(Font.DIALOG, Font.PLAIN, 12);
        return base.deriveFont(Font.BOLD, base.getSize2D() * SIZE_FACTOR);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(expressionFont());
        return new Dimension(metrics.stringWidth(text()) + 2 * PADDING, metrics.getHeight() + 2 * PADDING);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics2D.setFont(expressionFont());
            FontMetrics metrics = graphics2D.getFontMetrics();
            boolean dark = WizardColors.dark();
            int x = PADDING;
            int y = PADDING + metrics.getAscent();
            for (Run run : runs) {
                graphics2D.setColor(run.colorIndex() < 0 ? WizardColors.foreground()
                        : GroupPalette.color(run.colorIndex(), dark));
                graphics2D.drawString(run.text(), x, y);
                x += metrics.stringWidth(run.text());
            }
        } finally {
            graphics2D.dispose();
        }
    }
}
