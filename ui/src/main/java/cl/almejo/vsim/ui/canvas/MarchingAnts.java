package cl.almejo.vsim.ui.canvas;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;

/** The moving dash phase of selection outlines, and the two-tone stroke that is drawn with it. */
public final class MarchingAnts {

    private static final float DASH_LENGTH = 4f;
    private static final float GAP_LENGTH = 3f;
    /** One dash plus one gap: the phase wraps here, so the pattern repeats seamlessly. */
    public static final double PERIOD = DASH_LENGTH + GAP_LENGTH;
    private static final float DEFAULT_WIDTH = 1.5f;
    private static final Color UNDERLAY = Color.WHITE;

    private double phase;

    /** Moves the dashes one document pixel along. */
    public void advance() {
        phase = (phase + 1) % PERIOD;
    }

    public double phase() {
        return phase;
    }

    public BasicStroke stroke() {
        return stroke(DEFAULT_WIDTH, phase);
    }

    /** The dashed stroke of {@code width} whose pattern is shifted by {@code phase}. */
    public static BasicStroke stroke(float width, double phase) {
        return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[]{DASH_LENGTH, GAP_LENGTH}, (float) phase);
    }

    /** Draws {@code shape} as dashes of {@code color} over a white line, so it reads on any background. */
    public static void draw(Graphics2D graphics, Shape shape, Color color, float width, double phase) {
        graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        graphics.setColor(UNDERLAY);
        graphics.draw(shape);
        graphics.setStroke(stroke(width, phase));
        graphics.setColor(color);
        graphics.draw(shape);
    }
}
