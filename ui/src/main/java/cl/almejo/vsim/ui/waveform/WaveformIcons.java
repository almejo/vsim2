package cl.almejo.vsim.ui.waveform;

import java.util.List;

/**
 * The icons of the waveform panel toolbar, by file name under {@code icons/} (M6.1-E). «Acercar» and «Alejar» reuse
 * the canvas zoom icons.
 */
public final class WaveformIcons {

    public static final String FOLLOW = "waveform-follow";
    public static final String ZOOM_IN = "zoom-in";
    public static final String ZOOM_OUT = "zoom-out";
    public static final String FIT_ALL = "waveform-fit-all";
    public static final String SHOW_DELAYS = "waveform-delays";
    public static final String CLEAR = "waveform-clear";
    public static final String HIDE = "waveform-hide";

    private WaveformIcons() {
    }

    /** Every toolbar icon, in toolbar order. */
    public static List<String> names() {
        return List.of(FOLLOW, ZOOM_IN, ZOOM_OUT, FIT_ALL, SHOW_DELAYS, CLEAR, HIDE);
    }
}
