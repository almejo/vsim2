package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.paint.Appearance;

import javax.swing.JSplitPane;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;

/**
 * The width of an editor view's left column (palette and circuit tree). It is stored «at 100 %» of the interface size
 * ({@link AppPreferences#leftColumnWidth()}, default {@value #DEFAULT_WIDTH}) and applied × max(size, 100) / 100, never
 * under the 100 % width, and at most {@value #CAP_PERCENT} % of the split's width when it has one (so it is applied
 * again on the window's first layout and whenever the interface size changes). Only the user's drag stores, as
 * dragged ÷ factor; applying (including the size changes of presentation mode) stores nothing. EDT only.
 */
final class LeftColumnWidth {

    static final int DEFAULT_WIDTH = 230;
    static final int CAP_PERCENT = 30;

    private final JSplitPane split;
    private final AppPreferences preferences;
    private final Appearance appearance;
    private final LeftColumn leftColumn;
    private final Runnable zoomListener;
    private final PropertyChangeListener dragListener;
    private final ComponentAdapter resizeListener;
    private boolean applying;
    private boolean laidOut;

    /** Applies the width now and keeps applying it; {@link #dispose()} stops. */
    LeftColumnWidth(JSplitPane split, AppPreferences preferences, Appearance appearance,
                    LeftColumn leftColumn) {
        this.leftColumn = Objects.requireNonNull(leftColumn, "leftColumn");
        this.split = Objects.requireNonNull(split, "split");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.appearance = Objects.requireNonNull(appearance, "appearance");
        this.zoomListener = this::zoomChanged;
        this.dragListener = change -> dragged();
        this.resizeListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (!laidOut && split.getWidth() > 0) {
                    laidOut = true;
                    apply();
                }
            }
        };
        laidOut = split.getWidth() > 0;
        apply();
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dragListener);
        split.addComponentListener(resizeListener);
        appearance.addZoomListener(zoomListener);
    }

    private void zoomChanged() {
        apply();
        leftColumn.applyTreeHeight();
    }

    void dispose() {
        appearance.removeZoomListener(zoomListener);
        split.removeComponentListener(resizeListener);
        split.removePropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dragListener);
    }

    /** Puts the divider where the stored width and the current interface size say (capped); stores nothing. */
    void apply() {
        int target = scaled(preferences.leftColumnWidth().orElse(DEFAULT_WIDTH), appearance.interfaceZoom());
        if (split.getWidth() > 0) {
            target = Math.min(target, split.getWidth() * CAP_PERCENT / 100);
        }
        applying = true;
        try {
            split.setDividerLocation(target);
        } finally {
            applying = false;
        }
    }

    private void dragged() {
        if (applying || split.getWidth() <= 0) {
            return;
        }
        int pixels = split.getDividerLocation();
        if (pixels > 0) {
            preferences.setLeftColumnWidth(unscaled(pixels, appearance.interfaceZoom()));
        }
    }

    /** {@code atFullSize} pixels at the interface size {@code zoomPercent}: never smaller than at 100 %. */
    static int scaled(int atFullSize, int zoomPercent) {
        return (int) Math.round(atFullSize * Math.max(zoomPercent, 100) / 100.0);
    }

    /** The inverse of {@link #scaled}: what {@code pixels} are at 100 %. */
    static int unscaled(int pixels, int zoomPercent) {
        return (int) Math.round(pixels * 100.0 / Math.max(zoomPercent, 100));
    }
}
