package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.canvas.Viewport;

import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * The numbers behind the status-bar zoom control (zoom spec §6.2, §6.3): a logarithmic slider where every doubling is
 * the same distance (25 % at {@value #MINIMUM_VALUE}, 100 % at 0, 400 % at {@value #MAXIMUM_VALUE}) with a detent at
 * 100 %, and the percentages the field accepts.
 */
public final class ZoomScale {

    public static final int MINIMUM_VALUE = -200;
    public static final int MAXIMUM_VALUE = 200;
    /** Slider values this close to 0 (about ±3 %) snap to 100 %. */
    static final int DETENT = 4;
    private static final double VALUES_PER_DOUBLING = 100;
    private static final List<Integer> PRESETS = List.of(25, 50, 75, 100, 150, 200, 300, 400);
    /** A number with an optional sign and an optional decimal part after a comma or a point. */
    private static final Pattern NUMBER = Pattern.compile("-?(\\d+([.,]\\d+)?|[.,]\\d+)");

    private ZoomScale() {
    }

    /** Where the thumb sits for {@code zoom} (clamped to the zooms a view can show). */
    public static int sliderValue(double zoom) {
        return (int) Math.round(VALUES_PER_DOUBLING * Math.log(clamp(zoom)) / Math.log(2));
    }

    /** The zoom at slider value {@code value}; near the middle it is exactly 100 %. */
    public static double zoomOf(int value) {
        if (Math.abs(value) <= DETENT) {
            return 1.0;
        }
        return clamp(Math.pow(2, value / VALUES_PER_DOUBLING));
    }

    /**
     * The zoom written in {@code text}: «150», «150 %», «150%», « 62,5 », «62.5»; out of range it is clamped (1000 is
     * 400 %). Empty when the text is not a number.
     */
    public static OptionalDouble parsePercent(String text) {
        String number = text.replace(' ', ' ').replace(' ', ' ').strip();
        if (number.endsWith("%")) {
            number = number.substring(0, number.length() - 1).strip();
        }
        if (!NUMBER.matcher(number).matches()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(clamp(Double.parseDouble(number.replace(',', '.')) / 100));
    }

    /** {@code zoom} within the zooms a view can show (25 % to 400 %). */
    public static double clamp(double zoom) {
        return Viewport.clampZoom(zoom);
    }

    /** {@code zoom} as the whole percentage the field shows (1.5625 is 156). */
    public static long percent(double zoom) {
        return Math.round(zoom * 100);
    }

    /** The percentages offered in the field's list. */
    public static List<Integer> presets() {
        return PRESETS;
    }
}
