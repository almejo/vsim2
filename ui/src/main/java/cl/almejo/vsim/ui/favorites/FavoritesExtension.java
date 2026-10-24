package cl.almejo.vsim.ui.favorites;

import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/** «Favoritos» at the top of every window's palette, with the star that marks them (spec 2026-10-21). */
public final class FavoritesExtension implements EditorExtension {

    @Override
    public Runnable install(EditorView view) {
        PaletteFavorites favorites = new PaletteFavorites(view.palette(), view.services().preferences());
        favorites.install();
        return favorites::dispose;
    }
}
