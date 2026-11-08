package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.CircuitDocument;
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
 * The waveform panel of one view in presentation mode (the approved mockup v2 and the user's rules of 2026-11-08):
 * <ul>
 *   <li>Unpinned, the panel waits behind the bottom screen edge, at once on entering (nothing to drag), and comes up
 *       over the circuit, full width, after the dwell; its «Ocultar» slides it away and the edge keeps working.</li>
 *   <li>The edge brings it whenever the circuit has a probe, even with the panel closed; with no probes only an open
 *       panel is there, and closing it leaves the edge inert.</li>
 *   <li>Pinned (its pin, shown only in the mode and remembered in {@link AppPreferences}), it is docked and the canvas
 *       is shorter, also when it was closed. Its «Ocultar» puts it behind the edge for the rest of this time in the
 *       mode, the pin still remembered. Removing the pin leaves it where it is until the pointer leaves it.</li>
 *   <li>Leaving puts it back in the dock, open or closed and as tall as before entering, even if the divider was
 *       dragged in the mode.</li>
 * </ul>
 * EDT only.
 */
final class WaveformsInFullScreen {

    private final EditorView view;
    private final WaveformPanel panel;
    private final AppPreferences preferences;
    private final BottomDock dock;
    private final CircuitDocument document;
    /** The panel's holder behind the bottom edge: opaque, with the line on its top, as tall as the dock was. */
    private final JPanel edgeBar;
    private final Runnable dockListener = this::dockChanged;
    private final Runnable preferenceListener = this::update;
    private final Runnable stopFollowingTheLayout;
    /** The window's bars while this view is laid out for the mode; empty otherwise. */
    private Optional<EdgeBars> edgeBars = Optional.empty();
    /** The dock's height and state when this view was laid out for the mode, put back on leaving. */
    private int heightBeforeTheMode = BottomDock.DEFAULT_HEIGHT;
    private boolean openBeforeTheMode;
    private int heightOnTheEdge = BottomDock.DEFAULT_HEIGHT;
    /** A pinned panel hidden with its «Ocultar»: behind the edge until the mode ends or the pin is pressed. */
    private boolean unpinnedForNow;
    /** What takes the panel off the bottom edge; null while it is in the dock. */
    private Runnable unregister;
    /** Guards {@link #update()} against the dock events it causes itself. */
    private boolean updating;

    WaveformsInFullScreen(EditorView view, WaveformPanel panel, AppPreferences preferences) {
        this.view = view;
        this.panel = panel;
        this.preferences = preferences;
        this.dock = view.bottomDock();
        this.document = view.openDocument().document();
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

    /**
     * The panel's «Ocultar»: outside the mode it closes the dock. In the mode, with probes, the panel only goes out of
     * sight behind the edge (a pinned one for now); without probes it closes, and the edge with it.
     */
    void hide() {
        if (edgeBars.isEmpty() || !hasProbes()) {
            dock.hideDock();
        } else if (unregister == null) {
            unpinnedForNow = true;
            update();
        } else {
            edgeBars.get().hide(EdgeBars.Edge.BOTTOM);
        }
    }

    /** The probes changed: the first one wakes the bottom edge, removing the last puts it to sleep. */
    void probesChanged() {
        update();
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
            openBeforeTheMode = dock.isDockShowing();
            edgeBars = view.windowCommands().fullScreen().map(FullScreenFeatures::edgeBars);
            update();
        } else if (!inTheMode && edgeBars.isPresent()) {
            edgeBars = Optional.empty();
            unpinnedForNow = false;
            dock.setDockHeight(heightBeforeTheMode);
            update();
            restoreTheDockState();
        } else {
            update();
        }
    }

    private void restoreTheDockState() {
        if (openBeforeTheMode) {
            dock.showDock();
        } else {
            dock.hideDock();
        }
    }

    /** The panel was opened or closed (Ver ▸ Formas de onda): behind the edge it comes up or slides away. */
    private void dockChanged() {
        if (updating) {
            return;
        }
        update();
        if (unregister == null) {
            return;
        }
        if (dock.isDockShowing()) {
            reveal();
        } else {
            edgeBars.ifPresent(bars -> bars.hide(EdgeBars.Edge.BOTTOM));
        }
    }

    /** The pin was pressed: remembered for every view; unpinned, the panel stays until the pointer leaves it. */
    private void pinPressed() {
        boolean pinned = !isPinned();
        unpinnedForNow = false;
        preferences.setFullScreenWaveformsPinned(pinned);
        update();
        if (!pinned && unregister != null) {
            edgeBars.ifPresent(bars -> bars.showAtOnce(EdgeBars.Edge.BOTTOM));
        }
    }

    private boolean isPinned() {
        return preferences.fullScreenWaveformsPinned() && !unpinnedForNow;
    }

    private boolean hasProbes() {
        return !document.probes().isEmpty();
    }

    /** Puts the pin and the panel where the mode, the probes, the dock and the pin say. */
    private void update() {
        if (updating) {
            return;
        }
        updating = true;
        try {
            panel.showPinned(isPinned());
            panel.pinButton().setVisible(edgeBars.isPresent());
            boolean available = edgeBars.isPresent() && (hasProbes() || dock.isDockShowing());
            if (available && isPinned()) {
                dock.showDock();
            }
            boolean onTheEdge = available && !isPinned();
            if (onTheEdge && unregister == null) {
                moveToTheEdge();
            } else if (!onTheEdge && unregister != null) {
                moveToTheDock();
            }
        } finally {
            updating = false;
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
