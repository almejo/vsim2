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
    /** The pin of presentation mode, after the others (it shows only in the mode): tilted outline, unpinned. */
    public static final String PIN = "waveform-pin";
    /** The pressed pin: upright and filled, painted in the theme's red by {@link PinnedIcon}. */
    public static final String PINNED = "waveform-pinned";
    /** Ver ▸ Formas de onda and its toolbar toggle: a panel with a pulse train in its lower half. */
    public static final String PANEL = "waveforms";

    private WaveformIcons() {
    }

    /**
     * Every icon: the panel's toolbar in its order (the pin last, unpinned then pinned), then the panel's own toggle.
     */
    public static List<String> names() {
        return List.of(FOLLOW, ZOOM_IN, ZOOM_OUT, FIT_ALL, SHOW_DELAYS, CLEAR, HIDE, PIN, PINNED, PANEL);
    }
}
