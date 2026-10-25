package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.Optional;

/**
 * The small marks the mockups write as ⓘ, ⚠ and ⛔, painted rather than typed because those characters are missing
 * from many system fonts. Colours from the theme at each paint: the info circle in the muted text grey, the warning
 * triangle in {@code Actions.Yellow}, the blocking disc in {@code Actions.Red}.
 */
final class ProblemIcon implements Icon {
    private static final int SIZE = 14;
    private static final ProblemIcon INFO = new ProblemIcon(ToneLabel.Tone.INFO);
    private static final ProblemIcon WARNING = new ProblemIcon(ToneLabel.Tone.WARNING);
    private static final ProblemIcon BLOCKING = new ProblemIcon(ToneLabel.Tone.BLOCKING);
    private final ToneLabel.Tone tone;

    private ProblemIcon(ToneLabel.Tone tone) { this.tone = tone; }

    static Optional<Icon> of(ToneLabel.Tone tone) {
        return switch (tone) {
            case PLAIN -> Optional.empty();
            case INFO -> Optional.of(INFO);
            case WARNING -> Optional.of(WARNING);
            case BLOCKING -> Optional.of(BLOCKING);
        };
    }

    @Override public int getIconWidth() { return SIZE; }
    @Override public int getIconHeight() { return SIZE; }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D mark = (Graphics2D) graphics.create();
        try {
            mark.translate(x, y);
            mark.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            mark.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            switch (tone) {
                case INFO -> paintInfo(mark);
                case WARNING -> paintWarning(mark);
                case BLOCKING -> paintBlocking(mark);
                case PLAIN -> { }
            }
        } finally { mark.dispose(); }
    }

    private static void paintInfo(Graphics2D mark) {
        mark.setColor(ReadableColors.mutedText());
        mark.setStroke(new BasicStroke(1.1f));
        mark.draw(new Ellipse2D.Double(1, 1, SIZE - 2, SIZE - 2));
        mark.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        mark.draw(new Line2D.Double(SIZE / 2.0, 6.2, SIZE / 2.0, 10));
        mark.fill(new Ellipse2D.Double(SIZE / 2.0 - 0.9, 3.4, 1.8, 1.8));
    }

    private static void paintWarning(Graphics2D mark) {
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(SIZE / 2.0, 1);
        triangle.lineTo(SIZE - 0.5, SIZE - 1.5);
        triangle.lineTo(0.5, SIZE - 1.5);
        triangle.closePath();
        mark.setColor(themeColor("Actions.Yellow", new Color(0xEDA200)));
        mark.fill(triangle);
        mark.setColor(new Color(0x3A2A00));
        mark.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        mark.draw(new Line2D.Double(SIZE / 2.0, 5, SIZE / 2.0, 8.2));
        mark.fill(new Ellipse2D.Double(SIZE / 2.0 - 0.85, 9.4, 1.7, 1.7));
    }

    private static void paintBlocking(Graphics2D mark) {
        mark.setColor(themeColor("Actions.Red", new Color(0xDB5860)));
        mark.fill(new Ellipse2D.Double(0.5, 0.5, SIZE - 1, SIZE - 1));
        mark.setColor(Color.WHITE);
        mark.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        mark.draw(new Line2D.Double(3.8, SIZE / 2.0, SIZE - 3.8, SIZE / 2.0));
    }

    private static Color themeColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }
}
