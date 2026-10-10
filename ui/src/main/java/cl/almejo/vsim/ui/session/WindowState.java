package cl.almejo.vsim.ui.session;

import cl.almejo.vsim.ui.canvas.ViewState;

import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;

/**
 * Where a circuit window was and what it showed (spec §4.2): its normal (not maximized) bounds, whether it was
 * maximized, and its zoom and center ({@link ViewState}). Remembered by Archivo ▸ Recientes and the last session.
 */
public record WindowState(int x, int y, int width, int height, boolean maximized, ViewState view) {

    /** The window's top band, where its title bar is. */
    public static final int TITLE_BAND_HEIGHT = 80;
    /** How much of the title band must stay on some screen for the user to grab it. */
    public static final int MINIMUM_VISIBLE_WIDTH = 120;
    public static final int MINIMUM_VISIBLE_HEIGHT = 20;

    public WindowState {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Not a window size: " + width + " x " + height);
        }
        Objects.requireNonNull(view, "view");
    }

    public static WindowState of(Rectangle bounds, boolean maximized, ViewState view) {
        return new WindowState(bounds.x, bounds.y, bounds.width, bounds.height, maximized, view);
    }

    public Rectangle bounds() {
        return new Rectangle(x, y, width, height);
    }

    /**
     * This state when at least {@value #MINIMUM_VISIBLE_WIDTH} × {@value #MINIMUM_VISIBLE_HEIGHT} px of its top
     * {@value #TITLE_BAND_HEIGHT} px (less for a smaller window) is on one of {@code screens}, so a window a few pixels
     * above a screen or across stacked monitors keeps its place; otherwise the window centered on the first screen,
     * shrunk to fit it. No screens (headless) leaves it unchanged.
     */
    public WindowState clampedTo(List<Rectangle> screens) {
        if (screens.isEmpty()) {
            return this;
        }
        Rectangle titleBand = new Rectangle(x, y, width, Math.min(height, TITLE_BAND_HEIGHT));
        int neededWidth = Math.min(width, MINIMUM_VISIBLE_WIDTH);
        int neededHeight = Math.min(titleBand.height, MINIMUM_VISIBLE_HEIGHT);
        for (Rectangle screen : screens) {
            Rectangle visible = screen.intersection(titleBand);
            if (!visible.isEmpty() && visible.width >= neededWidth && visible.height >= neededHeight) {
                return this;
            }
        }
        Rectangle first = screens.get(0);
        int fittedWidth = Math.min(width, first.width);
        int fittedHeight = Math.min(height, first.height);
        return new WindowState(first.x + (first.width - fittedWidth) / 2, first.y + (first.height - fittedHeight) / 2,
                fittedWidth, fittedHeight, maximized, view);
    }
}
