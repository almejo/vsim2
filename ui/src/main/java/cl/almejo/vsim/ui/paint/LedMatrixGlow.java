package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.value.BitState;
import cl.almejo.vsim.core.value.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * «Brillo que se apaga» of the Matriz LED: how lit each LED looks, 1 right after it was lit and fading to 0 in
 * {@link #FADE_NANOS} of wall time once it is off. Purely visual: the simulation never knows. A view keeps one (the
 * canvas, on the EDT) and asks for the levels of each matrix on every frame it paints.
 *
 * <p>It fades only while the simulation time moves: paused, or stepping, the picture holds, so a paused matrix never
 * dims by itself. A view sees a matrix for the first time as it is (nothing fading). The fade advances on the frames
 * the canvas paints, so a matrix that stops changing keeps the level of its last frame until the next repaint.
 */
public final class LedMatrixGlow {

    /** How long an LED takes to go from fully lit to off: 300 ms. */
    public static final long FADE_NANOS = 300_000_000L;
    /** Above this many matrices remembered the memory is cleared (the ids of deleted ones). */
    private static final int MAXIMUM_REMEMBERED = 256;

    private final LongSupplier nanoClock;
    private final Map<String, Remembered> remembered = new HashMap<>();

    private record Remembered(double[][] levels, long nanos, long simulationTime) {
    }

    /** A glow on the system's clock. */
    public LedMatrixGlow() {
        this(System::nanoTime);
    }

    /** A glow on {@code nanoClock} (a fake one in tests). */
    public LedMatrixGlow(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    /**
     * The levels (row, column; column 0 on the left) of matrix {@code componentId} in this frame, for a {@code state}
     * from a snapshot at {@code simulationTime}. Each call is one frame of this view.
     */
    public double[][] levels(String componentId, VisibleState.LedMatrixState state, long simulationTime) {
        long now = nanoClock.getAsLong();
        List<Value> rows = state.rows();
        Remembered before = remembered.get(componentId);
        double fade = 0;
        if (before != null && simulationTime != before.simulationTime) {
            fade = (double) (now - before.nanos) / FADE_NANOS;
        }
        double[][] previous = before != null && sameSize(before.levels, rows) ? before.levels : null;
        double[][] levels = new double[rows.size()][];
        for (int row = 0; row < rows.size(); row++) {
            levels[row] = rowLevels(rows.get(row), previous == null ? null : previous[row], fade);
        }
        if (remembered.size() >= MAXIMUM_REMEMBERED && before == null) {
            remembered.clear();
        }
        remembered.put(componentId, new Remembered(levels, now, simulationTime));
        return levels;
    }

    private static boolean sameSize(double[][] levels, List<Value> rows) {
        return levels.length == rows.size() && levels[0].length == rows.get(0).width();
    }

    private static double[] rowLevels(Value value, double[] previous, double fade) {
        int columns = value.width();
        double[] levels = new double[columns];
        for (int column = 0; column < columns; column++) {
            boolean lit = !value.conflict() && value.bit(columns - 1 - column) == BitState.ONE;
            double fading = previous == null ? 0 : Math.max(0, previous[column] - fade);
            levels[column] = lit ? 1 : fading;
        }
        return levels;
    }
}
