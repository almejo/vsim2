package cl.almejo.vsim.ui.capture;

import java.util.List;

/** The icons of the screenshot button's two modes and of «Copiar captura», by file name under {@code icons/}. */
public final class CaptureIcons {

    public static final String WINDOW = "capture-window";
    public static final String VISIBLE_CIRCUIT = "capture-visible-circuit";

    private CaptureIcons() {
    }

    /** Every capture icon, once. */
    public static List<String> names() {
        return List.of(WINDOW, VISIBLE_CIRCUIT);
    }
}
