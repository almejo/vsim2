package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.problems.NetBlink;
import cl.almejo.vsim.ui.problems.ProblemNavigator;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.window.BottomDock;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.panes.EveryPane;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The waveform feature of one editor view (spec §3.2): the panel in the view's bottom dock, Ver ▸ Formas de onda,
 * the canvas context-menu items, «Mostrar en el circuito», the probe listener and (M6.1-E) the markers of the
 * monitored nets on the canvas with their tooltip and click. {@link #dispose()} removes what it registered on shared
 * objects.
 */
public final class WaveformFeatures {

    private final EditorView view;
    private final BiConsumer<Component, String> messages;
    private final ProbeActions actions;
    private final NetBlink blink;
    private final WaveformPanel panel;
    private final Action toggle;
    private final Runnable dockListener;
    private final JMenu viewMenu;
    private final Component separator;
    private final JCheckBoxMenuItem toggleItem;
    private final Runnable shortcutRemover;
    private final Runnable probeListener;
    private final ProbeMarkerOverlay markers;
    private final Runnable removeFromPanes;
    private boolean disposed;

    public WaveformFeatures(EditorView view, TextPrompt prompt, BiConsumer<Component, String> messages) {
        this.view = view;
        this.messages = messages;
        OpenDocument openDocument = view.openDocument();
        BottomDock dock = view.bottomDock();
        this.actions = new ProbeActions(openDocument);
        Runnable repaintPanes = EveryPane.repaint(view);
        this.blink = new NetBlink(() -> view.canvas().scheme(), repaintPanes);
        this.panel = new WaveformPanel(openDocument, actions, () -> view.canvas().scheme(), prompt, this::showInCircuit,
                dock::hideDock);
        dock.setContent(panel);
        this.toggle = new AbstractAction(Messages.text("menu.view.waveforms")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (dock.isDockShowing()) {
                    dock.hideDock();
                } else {
                    dock.showDock();
                }
            }
        };
        this.dockListener = () -> toggle.putValue(Action.SELECTED_KEY, dock.isDockShowing());
        dock.addDockListener(dockListener);
        dockListener.run();
        this.viewMenu = view.menu(MenuId.VIEW);
        viewMenu.addSeparator();
        this.separator = viewMenu.getMenuComponent(viewMenu.getMenuComponentCount() - 1);
        this.toggleItem = new JCheckBoxMenuItem(toggle);
        viewMenu.add(toggleItem);
        this.shortcutRemover = view.registerShortcutAction("waveforms.toggle", toggle);
        view.addContextMenuContributor(new WaveformMenuContributor(actions, this::afterAdd));
        this.markers = new ProbeMarkerOverlay(openDocument, () -> view.canvas().scheme());
        this.removeFromPanes = view.forEachPane(this::installOn);
        this.probeListener = () -> {
            panel.probesChanged();
            repaintPanes.run(); // probe edits do not notify document listeners: the markers must repaint
        };
        openDocument.document().addProbeListener(probeListener);
        if (!openDocument.document().probes().isEmpty()) {
            dock.showDock();
        }
    }

    /** The blink, the markers, their tooltip and their click in {@code pane}. Returns what undoes it. */
    private Runnable installOn(CircuitCanvas pane) {
        pane.addOverlay(blink);
        pane.addOverlay(markers);
        Runnable toolTipRemover = pane.addToolTipOverride(this::markerToolTip);
        Runnable pressRemover = pane.addPressInterceptor(event -> markerPressed(pane, event));
        return () -> {
            pressRemover.run();
            toolTipRemover.run();
            pane.removeOverlay(markers);
            pane.removeOverlay(blink);
        };
    }

    public WaveformPanel panel() {
        return panel;
    }

    public ProbeActions actions() {
        return actions;
    }

    NetBlink blink() {
        return blink;
    }

    /** «En formas de onda: U3.q» over a marker; two channels on one anchor list both names. */
    Optional<String> markerToolTip(Point2D gridPoint) {
        return markers.markerAt(gridPoint)
                .map(marker -> Messages.format("waveform.markerTip", String.join(", ", marker.names())));
    }

    /**
     * A primary press on a marker in {@code pane} with its select tool: opens the panel if hidden, selects the first
     * channel of the marker and blinks its row. True consumes the press (the select tool never sees it).
     */
    boolean markerPressed(CircuitCanvas pane, CanvasMouseEvent event) {
        if (disposed || !(pane.tool() instanceof SelectTool)) {
            return false;
        }
        Optional<ProbeMarker> marker = markers.markerAt(event.gridPosition());
        if (marker.isEmpty()) {
            return false;
        }
        String probeId = marker.get().probeIds().getFirst();
        view.bottomDock().showDock();
        panel.selectRow(probeId);
        panel.blinkRow(probeId);
        return true;
    }

    ProbeMarkerOverlay markers() {
        return markers;
    }

    /**
     * After a context-menu add: open the panel, report the cap, blink the row of a net that was already shown. An
     * add that found nothing (no channel added, none already shown, cap not reached) leaves the panel as it was.
     */
    void afterAdd(ProbeActions.AddResult result) {
        if (disposed || !result.addedAny() && result.existingId().isEmpty() && !result.capReached()) {
            return;
        }
        view.bottomDock().showDock();
        if (result.capReached()) {
            messages.accept(view, Messages.format("waveform.capReached", ProbeActions.MAXIMUM_CHANNELS));
        }
        if (!result.addedAny()) {
            result.existingId().ifPresent(panel::blinkRow);
        }
    }

    /**
     * «Mostrar en el circuito»: centers the active pane on the probe's net, selects its wires (or its components) and
     * blinks them.
     */
    void showInCircuit(String probeId) {
        if (disposed) {
            return;
        }
        OpenDocument openDocument = view.openDocument();
        String netId = openDocument.probeNets().get(probeId);
        if (netId == null) {
            return;
        }
        Optional<Net> net = openDocument.netlist().net(new NetId(netId));
        if (net.isEmpty()) {
            return;
        }
        ProblemNavigator.focusNet(view.canvas(), blink, net.get());
    }

    /**
     * After a step (M6.1-B «Avanzar»): when {@code view}'s waveform panel is showing, its cursor goes to {@code time}.
     * Does nothing when the panel is hidden or the view has no waveform panel.
     */
    public static void followStep(EditorView view, long time) {
        BottomDock dock = view.bottomDock();
        if (dock.isDockShowing() && dock.content().orElse(null) instanceof WaveformPanel panel) {
            panel.placeCursorAtTime(time);
        }
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        removeFromPanes.run();
        shortcutRemover.run();
        view.openDocument().document().removeProbeListener(probeListener);
        view.bottomDock().removeDockListener(dockListener);
        blink.dispose();
        panel.dispose();
        viewMenu.remove(toggleItem);
        viewMenu.remove(separator);
        view.bottomDock().hideDock();
        view.bottomDock().setContent(null);
    }
}
