package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * The time axis of a waveform panel (pure, no Swing): how many pixels one simulation unit takes, which time sits at
 * the left edge of the plot, whether the right edge follows the current time («Seguir»), and the cursor. {@code x}
 * is measured in pixels from the left edge of the plot.
 */
public final class TimeAxis {

    /** Gate delays: one unit is 40 px wide. */
    public static final double MAXIMUM_SCALE = 40;
    /** Many default clock periods (2 000 units each) on one screen. */
    public static final double MINIMUM_SCALE = 1.0 / 2000;
    /** «Ver retardos»: a 1-unit delay is 20 px, easy to count. */
    public static final double DELAY_SCALE = 20;
    /** From this scale on a faint line marks every unit. */
    public static final double UNIT_GRID_SCALE = 8;
    /** What a new panel shows: the last 4 000 units. */
    public static final long INITIAL_SPAN = 4000;
    /** A click this close to an edge puts the cursor on the edge. */
    public static final double SNAP_PIXELS = 4;
    static final double MINIMUM_TICK_PIXELS = 80;
    static final double ZOOM_STEP = 2;

    private double scale = 1;
    private boolean scaleChosen;
    private double startTime;
    private int width;
    private boolean following = true;
    private OptionalLong cursor = OptionalLong.empty();

    /** Pixels per simulation unit. */
    public double scale() {
        return scale;
    }

    /** The time at the left edge of the plot. */
    public double startTime() {
        return startTime;
    }

    public int width() {
        return width;
    }

    public boolean isFollowing() {
        return following;
    }

    public OptionalLong cursor() {
        return cursor;
    }

    /** The plot is {@code newWidth} pixels wide; the first positive width shows the last {@link #INITIAL_SPAN} units. */
    public void setWidth(int newWidth) {
        width = Math.max(0, newWidth);
        if (!scaleChosen && width > 0) {
            scale = clamp(width / (double) INITIAL_SPAN);
            scaleChosen = true;
        }
    }

    public double xOf(double time) {
        return (time - startTime) * scale;
    }

    public double timeAt(double x) {
        return startTime + x / scale;
    }

    /** The time at the right edge of the plot. */
    public double endTime() {
        return timeAt(width);
    }

    /** While following, puts {@code now} at the right edge. */
    public void follow(long now) {
        if (following) {
            startTime = now - width / scale;
        }
    }

    /** «Seguir» on or off; turning it on takes effect at the next {@link #follow}. */
    public void setFollowing(boolean enabled) {
        following = enabled;
    }

    /** Moves the view {@code pixels} towards later times (negative: earlier) and stops following. */
    public void scrollBy(double pixels) {
        startTime += pixels / scale;
        following = false;
    }

    /**
     * Multiplies the scale by {@code factor} (clamped), keeping the time under {@code x} in place; while following,
     * the right edge stays in place instead.
     */
    public void zoomAround(double x, double factor) {
        double anchorX = following ? width : x;
        double anchorTime = timeAt(anchorX);
        scale = clamp(scale * factor);
        scaleChosen = true;
        startTime = anchorTime - anchorX / scale;
    }

    public void zoomIn() {
        zoomAround(width / 2.0, ZOOM_STEP);
    }

    public void zoomOut() {
        zoomAround(width / 2.0, 1 / ZOOM_STEP);
    }

    /** «Ver todo»: shows {@code from} to {@code to} across the plot (scale clamped) and stops following. */
    public void fit(long from, long to) {
        if (width <= 0) {
            return;
        }
        following = false;
        scale = clamp(width / (double) Math.max(1, to - from));
        scaleChosen = true;
        startTime = from;
    }

    /** «Ver retardos»: {@link #DELAY_SCALE} with {@code around} in the middle; stops following. */
    public void showDelays(long around) {
        following = false;
        scale = DELAY_SCALE;
        scaleChosen = true;
        startTime = around - width / 2.0 / scale;
    }

    /** Puts {@code time} in the middle of the plot at the current scale and stops following. */
    public void centerOn(long time) {
        following = false;
        startTime = time - width / 2.0 / scale;
    }

    /** Units between ruler ticks: the smallest 1, 2 or 5 × 10ⁿ at least {@link #MINIMUM_TICK_PIXELS} apart. */
    public long tickSpacing() {
        long magnitude = 1;
        while (true) {
            for (long step : new long[]{1, 2, 5}) {
                long spacing = step * magnitude;
                if (spacing * scale >= MINIMUM_TICK_PIXELS) {
                    return spacing;
                }
            }
            magnitude *= 10;
        }
    }

    /** The tick times inside the plot, every {@link #tickSpacing()} units. */
    public List<Long> ticks() {
        long spacing = tickSpacing();
        List<Long> ticks = new ArrayList<>();
        long first = (long) Math.ceil(startTime / spacing) * spacing;
        for (long tick = first; tick <= endTime(); tick += spacing) {
            ticks.add(tick);
        }
        return ticks;
    }

    public boolean showsUnitGrid() {
        return scale >= UNIT_GRID_SCALE;
    }

    public void setCursor(long time) {
        cursor = OptionalLong.of(time);
    }

    /** «t = 1 237»: {@code time} as the panel labels it. */
    public static String timeLabel(long time) {
        return Messages.format("waveform.time", format(time));
    }

    /** Where a click at {@code x} puts the cursor: the nearest of {@code edgeTimes} within {@link #SNAP_PIXELS}, else the time under x. */
    public long snap(double x, Collection<Long> edgeTimes) {
        long result = Math.round(timeAt(x));
        double nearest = Double.POSITIVE_INFINITY;
        for (long edge : edgeTimes) {
            double distance = Math.abs(xOf(edge) - x);
            if (distance <= SNAP_PIXELS && distance < nearest) {
                nearest = distance;
                result = edge;
            }
        }
        return result;
    }

    /** {@code time} with its thousands separated by spaces: «12 345» (notation, the same in every language). */
    public static String format(long time) {
        return String.format(Locale.ROOT, "%,d", time).replace(',', ' ');
    }

    private static double clamp(double candidate) {
        return Math.max(MINIMUM_SCALE, Math.min(MAXIMUM_SCALE, candidate));
    }
}
