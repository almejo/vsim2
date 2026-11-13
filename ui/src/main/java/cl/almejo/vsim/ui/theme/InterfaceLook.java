package cl.almejo.vsim.ui.theme;

import cl.almejo.vsim.ui.app.AppPreferences;

import java.util.Objects;
import java.util.Optional;

/**
 * The size and font of the whole interface (Preferencias, Aspecto, Ventana): {@code zoomPercent} of the look and
 * feel's own font, which also scales its insets and icons, and the font family ({@link Optional#empty} is the look and
 * feel's own). It is apart from the canvas zoom and from the circuit's «Letra de etiquetas y textos».
 */
public record InterfaceLook(int zoomPercent, Optional<String> fontFamily) {

    /** The look and feel's own size and font. */
    public static final InterfaceLook STANDARD = new InterfaceLook(100, Optional.empty());

    public InterfaceLook {
        Objects.requireNonNull(fontFamily, "fontFamily");
        if (zoomPercent <= 0) {
            throw new IllegalArgumentException("zoomPercent must be positive: " + zoomPercent);
        }
    }

    /**
     * What the preferences ask for; while {@code presenting} (a window is in «Modo presentación») the presentation size
     * when it is larger, never smaller.
     */
    public static InterfaceLook of(AppPreferences preferences, boolean presenting) {
        int zoom = preferences.interfaceZoom();
        int presentationZoom = preferences.presentationInterfaceZoom();
        if (presenting && presentationZoom != AppPreferences.PRESENTATION_ZOOM_UNCHANGED) {
            zoom = Math.max(zoom, presentationZoom);
        }
        return new InterfaceLook(zoom, preferences.interfaceFontFamily());
    }
}
