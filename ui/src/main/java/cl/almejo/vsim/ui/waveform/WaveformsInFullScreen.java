package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.fullscreen.EdgeBars;
import cl.almejo.vsim.ui.fullscreen.FullScreenFeatures;
import cl.almejo.vsim.ui.window.BottomDock;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.util.Optional;

/**
 * The waveform panel of one view in presentation mode (the approved mockup v2): unpinned, the open panel leaves the
 * bottom dock at once and waits behind the bottom screen edge, coming up over the circuit, full width, after the
 * dwell; pinned (its pin, shown only in the mode and remembered in {@link AppPreferences}), it stays docked and the
 * canvas is shorter. Removing the pin leaves the panel where it is until the pointer leaves it. Leaving the mode puts
 * the panel back in the dock as tall as it was before entering, even if the divider was dragged in the mode. With the
 * panel closed nothing is behind the bottom edge, so it does nothing. EDT only.
 */
final class WaveformsInFullScreen {

    private final EditorView view;
    private final WaveformPanel panel;
    private final AppPreferences preferences;
    private final BottomDock dock;
    /** The panel's holder behind the bottom edge: opaque, with the line on its top, as tall as the dock was. */
    private final JPanel edgeBar;
    private final Runnable dockListener = this::dockChanged;
    private final Runnable preferenceListener = this::update;
    private final Runnable stopFollowingTheLayout;
    /** The window's bars while this view is laid out for the mode; empty otherwise. */
    private Optional<EdgeBars> edgeBars = Optional.empty();
    /** The dock's height when this view was laid out for the mode, put back on leaving. */
    private int heightBeforeTheMode = BottomDock.DEFAULT_HEIGHT;
    private int heightOnTheEdge = BottomDock.DEFAULT_HEIGHT;
    /** What takes the panel off the bottom edge; null while it is in the dock. */
    private Runnable unregister;

    WaveformsInFullScreen(EditorView view, WaveformPanel panel, AppPreferences preferences) {
        this.view = view;
        this.panel = panel;
        this.preferences = preferences;
        this.dock = view.bottomDock();
        this.edgeBar = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, heightOnTheEdge);
            }
        };
        edgeBar.setOpaque(true);
        edgeBar.setBorder(EdgeBars.innerLine(EdgeBars.Edge.BOTTOM));
        panel.pinButton().addActionListener(event -> pinPressed());
        dock.addDockListener(dockListener);
        preferences.addListener(preferenceListener);
        this.stopFollowingTheLayout = view.addFullScreenLayoutListener(this::layoutChanged);
        layoutChanged();
    }

    /**
     * Something the user did elsewhere wants the panel seen (a click on a probe marker, «Ver en formas de onda»):
     * behind the bottom edge it comes up for a moment; docked or outside the mode nothing changes.
     */
    void reveal() {
        if (unregister != null) {
            edgeBars.ifPresent(bars -> bars.reveal(EdgeBars.Edge.BOTTOM));
        }
    }

    void dispose() {
        stopFollowingTheLayout.run();
        preferences.removeListener(preferenceListener);
        dock.removeDockListener(dockListener);
        if (unregister != null) {
            moveToTheDock();
        }
    }

    /** The view was laid out for presentation mode, or back. */
    private void layoutChanged() {
        boolean inTheMode = view.fullScreenStage().isPresent();
        if (inTheMode && edgeBars.isEmpty()) {
            heightBeforeTheMode = dock.dockHeight();
            edgeBars = view.windowCommands().fullScreen().map(FullScreenFeatures::edgeBars);
        } else if (!inTheMode && edgeBars.isPresent()) {
            edgeBars = Optional.empty();
            dock.setDockHeight(heightBeforeTheMode);
        }
        update();
    }

    /** The panel was opened or closed: opened in the mode, it comes up from the edge for a moment. */
    private void dockChanged() {
        update();
        if (dock.isDockShowing()) {
            reveal();
        }
    }

    /** The pin was pressed: remembered for every view; unpinned, the panel stays until the pointer leaves it. */
    private void pinPressed() {
        boolean pinned = !preferences.fullScreenWaveformsPinned();
        preferences.setFullScreenWaveformsPinned(pinned);
        update();
        if (!pinned && unregister != null) {
            edgeBars.ifPresent(bars -> bars.showAtOnce(EdgeBars.Edge.BOTTOM));
        }
    }

    /** Puts the pin and the panel where the mode, the dock and the pin say. */
    private void update() {
        boolean pinned = preferences.fullScreenWaveformsPinned();
        panel.showPinned(pinned);
        panel.pinButton().setVisible(edgeBars.isPresent());
        boolean onTheEdge = edgeBars.isPresent() && dock.isDockShowing() && !pinned;
        if (onTheEdge && unregister == null) {
            moveToTheEdge();
        } else if (!onTheEdge && unregister != null) {
            moveToTheDock();
        }
    }

    private void moveToTheEdge() {
        Optional<JLayeredPane> stage = view.fullScreenStage();
        if (stage.isEmpty() || edgeBars.isEmpty() || dock.lendContent().isEmpty()) {
            return;
        }
        heightOnTheEdge = dock.dockHeight();
        edgeBar.add(panel, BorderLayout.CENTER);
        stage.get().add(edgeBar, JLayeredPane.PALETTE_LAYER);
        unregister = edgeBars.get().register(EdgeBars.Edge.BOTTOM, edgeBar, () -> false);
        stage.get().revalidate();
        stage.get().repaint();
    }

    private void moveToTheDock() {
        unregister.run();
        unregister = null;
        Container stage = edgeBar.getParent();
        if (stage != null) {
            stage.remove(edgeBar);
            stage.repaint();
        }
        dock.returnContent();
    }
}
