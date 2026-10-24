package cl.almejo.vsim.ui.favorites;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.panels.PalettePanel;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps one palette's «Favoritos» in step with the favourites saved in the app preferences (shared by every window
 * and circuit) and saves the star's marks there. Listeners run on the EDT; {@link #dispose()} removes them.
 */
public final class PaletteFavorites {

    private final PalettePanel palette;
    private final AppPreferences preferences;
    private final Runnable refresher = this::refresh;

    public PaletteFavorites(PalettePanel palette, AppPreferences preferences) {
        this.palette = palette;
        this.preferences = preferences;
    }

    public void install() {
        palette.setFavoriteToggle(this::toggle);
        preferences.addListener(refresher);
        refresh();
    }

    public void dispose() {
        preferences.removeListener(refresher);
    }

    /** The palette ignores an unchanged list, so other preference changes leave it alone. */
    private void refresh() {
        palette.setFavorites(preferences.paletteFavorites());
    }

    /** Marks {@code typeId} as the last favourite, or unmarks it when it already is one. */
    private void toggle(String typeId) {
        List<String> next = new ArrayList<>(preferences.paletteFavorites());
        if (!next.remove(typeId)) {
            next.add(typeId);
        }
        preferences.setPaletteFavorites(next);
    }
}
