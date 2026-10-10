package cl.almejo.vsim.ui.canvas;

/** The slow pulse of the red glow under problem wires: a phase in [0, 1) and the glow opacity it stands for. */
public final class ProblemPulse {

    /** The phase of the dimmest glow. */
    public static final double LOW_PHASE = 0;
    /** The phase of the brightest glow. */
    public static final double HIGH_PHASE = 0.5;
    /** One full dim-bright-dim cycle. */
    public static final int PERIOD_MILLISECONDS = 1200;
    /** How often the clock repaints; a pulse this slow looks smooth at about 20 frames per second. */
    public static final int TICK_MILLISECONDS = 50;
    static final int MINIMUM_ALPHA = 15;
    static final int MAXIMUM_ALPHA = 110;

    private double phase;

    /** Moves one tick along the cycle, wrapping back to 0 after a full period. */
    public void advance() {
        phase = (phase + (double) TICK_MILLISECONDS / PERIOD_MILLISECONDS) % 1.0;
    }

    public double phase() {
        return phase;
    }

    /** The glow alpha (0-255) at {@code phase}: a sine between {@link #MINIMUM_ALPHA} and {@link #MAXIMUM_ALPHA}. */
    public static int alpha(double phase) {
        double brightness = 0.5 - 0.5 * Math.cos(2 * Math.PI * phase);
        return (int) Math.round(MINIMUM_ALPHA + (MAXIMUM_ALPHA - MINIMUM_ALPHA) * brightness);
    }
}
