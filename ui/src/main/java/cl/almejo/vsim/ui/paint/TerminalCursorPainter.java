package cl.almejo.vsim.ui.paint;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * The seven cursors of a Terminal (spec §8.5, the mockup's {@code cursorStyle} and {@code automanFrame}), drawn on one
 * cell in the screen's text colour. The block's inverted character is the Terminal painter's business; this class
 * draws only the shapes.
 *
 * <p>Automan's cursor is a glowing seven-pointed star, white to cyan with a cyan halo, a little larger than the cell
 * is wide. Given a time it is the mockup's chaotic version: each point grows and shrinks on its own rhythm while the
 * star spins irregularly, wobbles a pixel or two, pulses in size and flickers in brightness; without one (NaN: reduced
 * motion, an inactive window, an export) it is a still, regular star.
 */
final class TerminalCursorPainter {

    /** Width of the bar, the underline and the I-beam's bar, and of the I-beam's serifs' height. */
    private static final double THICK = 2;
    /** The hollow block's outline. */
    private static final double THIN = 1;
    /** Where the I-beam's bar starts from the cell's left, and how wide its serifs are. */
    private static final double IBEAM_BAR_LEFT = 2;
    private static final double IBEAM_SERIF_WIDTH = 6;

    /** The star's box against the cell's height: the mockup's 1.2 em in a line of about 1.3 em. */
    private static final double STAR_BOX_SHARE = 0.92;
    private static final int STAR_POINTS = 7;
    /** The mockup draws in percent of its box: 50 % is the box's half. */
    private static final double PERCENT = 100;
    private static final double HALF_BOX_PERCENT = 50;
    private static final double STILL_INNER_SHARE = 0.44;
    private static final Color STAR_WHITE = Color.WHITE;
    private static final Color STAR_PALE_CYAN = new Color(0x8FE9FF);
    private static final Color STAR_CYAN = new Color(0x3CB4E5);
    private static final float[] STAR_GRADIENT_STOPS = {0f, 0.5f, 1f};
    /** The halo, the mockup's two drop shadows: the star grown and drawn faint under itself. */
    private static final double OUTER_HALO_GROWTH = 1.25;
    private static final double INNER_HALO_GROWTH = 1.12;
    private static final int OUTER_HALO_ALPHA = 70;
    private static final int INNER_HALO_ALPHA = 110;
    private static final int MAXIMUM_CHANNEL = 255;

    private TerminalCursorPainter() {
    }

    /**
     * Draws {@code shape} on {@code cell} in {@code color}; Automan's star at {@code automanSeconds} (NaN: still).
     * Every shape stays inside the cell except Automan's star, which stays inside {@link #reach}.
     */
    static void paint(Graphics2D graphics, TerminalCursorShape shape, Rectangle2D cell, Color color,
                      double automanSeconds) {
        double left = cell.getX();
        double top = cell.getY();
        double width = cell.getWidth();
        double height = cell.getHeight();
        graphics.setColor(color);
        switch (shape) {
            case BLOCK -> graphics.fill(cell);
            case HOLLOW -> {
                graphics.fill(new Rectangle2D.Double(left, top, width, THIN));
                graphics.fill(new Rectangle2D.Double(left, top + height - THIN, width, THIN));
                graphics.fill(new Rectangle2D.Double(left, top, THIN, height));
                graphics.fill(new Rectangle2D.Double(left + width - THIN, top, THIN, height));
            }
            case UNDERLINE -> graphics.fill(new Rectangle2D.Double(left, top + height - THICK, width, THICK));
            case BAR -> graphics.fill(new Rectangle2D.Double(left, top, THICK, height));
            case IBEAM -> {
                double serifWidth = Math.min(IBEAM_SERIF_WIDTH, width);
                graphics.fill(new Rectangle2D.Double(left + Math.min(IBEAM_BAR_LEFT, width - THICK), top, THICK,
                        height));
                graphics.fill(new Rectangle2D.Double(left, top, serifWidth, THICK));
                graphics.fill(new Rectangle2D.Double(left, top + height - THICK, serifWidth, THICK));
            }
            case AUTOMAN -> paintStar(graphics, cell, automanSeconds);
            case NONE -> {
            }
        }
    }

    /** Where a cursor on {@code cell} may draw: the cell and, for Automan's star and halo, half its height around. */
    static Rectangle2D reach(Rectangle2D cell) {
        double margin = cell.getHeight() / 2;
        return new Rectangle2D.Double(cell.getX() - margin, cell.getY() - margin, cell.getWidth() + 2 * margin,
                cell.getHeight() + 2 * margin);
    }

    private static void paintStar(Graphics2D graphics, Rectangle2D cell, double seconds) {
        // The coefficients below are the mockup's automanFrame, kept literal so the two can be compared line by line.
        boolean still = Double.isNaN(seconds);
        double box = cell.getHeight() * STAR_BOX_SHARE;
        Point2D centre = still ? new Point2D.Double(cell.getCenterX(), cell.getCenterY())
                : new Point2D.Double(cell.getCenterX() + Math.sin(seconds * 2.9) * 1.5,
                cell.getCenterY() + Math.sin(seconds * 2.1 + 1) * 2);
        double scale = still ? 1 : 0.85 + 0.2 * Math.sin(seconds * 5.7);
        double brightness = still ? 1 : 1 + 0.35 * Math.abs(Math.sin(seconds * 7.3));
        double[] radii = still ? stillRadii() : movingRadii(seconds);
        double spin = still ? -Math.PI / 2 : seconds * 2.3 + Math.sin(seconds * 1.3) * 0.9
                + Math.sin(seconds * 4.1) * 0.25;
        double unit = box / PERCENT * scale;
        Graphics2D star = (Graphics2D) graphics.create();
        try {
            star.setColor(PainterSupport.withAlpha(STAR_CYAN, OUTER_HALO_ALPHA));
            star.fill(starPath(centre, radii, spin, unit * OUTER_HALO_GROWTH));
            star.setColor(PainterSupport.withAlpha(STAR_PALE_CYAN, INNER_HALO_ALPHA));
            star.fill(starPath(centre, radii, spin, unit * INNER_HALO_GROWTH));
            star.setPaint(new RadialGradientPaint(centre, (float) (HALF_BOX_PERCENT * unit), STAR_GRADIENT_STOPS,
                    new Color[] {brighter(STAR_WHITE, brightness), brighter(STAR_PALE_CYAN, brightness),
                            brighter(STAR_CYAN, brightness)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
            star.fill(starPath(centre, radii, spin, unit));
        } finally {
            star.dispose();
        }
    }

    /** The mockup's radii, in percent of the box: the points grow and shrink each on its own rhythm. */
    private static double[] movingRadii(double seconds) {
        double[] radii = new double[STAR_POINTS * 2];
        for (int index = 0; index < radii.length; index++) {
            radii[index] = index % 2 == 0
                    ? HALF_BOX_PERCENT * (0.45 + 0.55 * Math.abs(Math.sin(seconds * (1.6 + index * 0.41) + index * 1.9)))
                    : HALF_BOX_PERCENT * (0.16 + 0.12 * Math.sin(seconds * 3.3 + index * 2.7));
        }
        return radii;
    }

    /** A regular star: every point at the box's edge, the notches in between. */
    private static double[] stillRadii() {
        double[] radii = new double[STAR_POINTS * 2];
        for (int index = 0; index < radii.length; index++) {
            radii[index] = index % 2 == 0 ? HALF_BOX_PERCENT : HALF_BOX_PERCENT * STILL_INNER_SHARE;
        }
        return radii;
    }

    private static Path2D starPath(Point2D centre, double[] radii, double spin, double unit) {
        Path2D.Double path = new Path2D.Double();
        for (int index = 0; index < radii.length; index++) {
            double angle = spin + index * Math.PI / STAR_POINTS;
            double x = centre.getX() + radii[index] * unit * Math.cos(angle);
            double y = centre.getY() + radii[index] * unit * Math.sin(angle);
            if (index == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
    }

    /** The mockup's {@code brightness()} filter: every channel times {@code factor}, at most full. */
    private static Color brighter(Color color, double factor) {
        return new Color(channel(color.getRed(), factor), channel(color.getGreen(), factor),
                channel(color.getBlue(), factor));
    }

    private static int channel(int value, double factor) {
        return (int) Math.min(MAXIMUM_CHANNEL, Math.round(value * factor));
    }
}
