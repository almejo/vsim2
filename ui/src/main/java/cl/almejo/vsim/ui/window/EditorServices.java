package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;

import java.util.List;
import java.util.Objects;

/** Application-wide objects every editor view shares, plus the extensions installed into each view. */
public record EditorServices(PainterRegistry painters, Appearance appearance, AppPreferences preferences,
                             ComponentLibrary library, List<EditorExtension> extensions) {

    public EditorServices {
        Objects.requireNonNull(painters, "painters");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(preferences, "preferences");
        Objects.requireNonNull(library, "library");
        extensions = List.copyOf(extensions);
    }

    /** Fixed scheme, in-memory preferences, an empty library and no extensions: tests and simple tools. */
    public static EditorServices of(PainterRegistry painters, ColorScheme scheme) {
        return new EditorServices(painters, new Appearance(scheme), AppPreferences.inMemory(), new ComponentLibrary(),
                List.of());
    }

    public EditorServices withExtensions(List<EditorExtension> newExtensions) {
        return new EditorServices(painters, appearance, preferences, library, newExtensions);
    }

    /** The same services read through {@code windowPreferences} (one window's {@link AppPreferences#layered()}). */
    public EditorServices withPreferences(AppPreferences windowPreferences) {
        return new EditorServices(painters, appearance, windowPreferences, library, extensions);
    }
}
