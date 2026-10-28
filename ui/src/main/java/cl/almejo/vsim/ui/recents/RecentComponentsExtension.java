package cl.almejo.vsim.ui.recents;

import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;

/**
 * «Recientes» in every window's palette: the components that actually landed in a circuit from the palette's world
 * (a click with the placing tool, a drop, a quick insert, a user component), told by each pane's placement
 * listener. The application creates this extension once, so its one {@link RecentComponents} is shared by every
 * window; each palette follows it from {@link #install} until its window closes.
 */
public final class RecentComponentsExtension implements EditorExtension {

    private final RecentComponents recents = new RecentComponents();

    /** The shared list. Package-private for tests. */
    RecentComponents recents() {
        return recents;
    }

    @Override
    public Runnable install(EditorView view) {
        PalettePanel palette = view.palette();
        Runnable stopFollowing = recents.addListener(() -> palette.setRecents(recents.typeIds()));
        palette.setRecents(recents.typeIds());
        Runnable stopCounting = view.forEachPane(canvas ->
                canvas.addPlacementListener(recents::used));
        return () -> {
            stopCounting.run();
            stopFollowing.run();
        };
    }
}
