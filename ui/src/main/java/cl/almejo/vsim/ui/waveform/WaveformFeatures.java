package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.problems.NetBlink;
import cl.almejo.vsim.ui.problems.ProblemNavigator;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.window.BottomDock;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuSlot;
import cl.almejo.vsim.ui.window.MenuSlots;
import cl.almejo.vsim.ui.window.panes.EveryPane;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

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
    private final Runnable menuRemover;
    private final Runnable shortcutRemover;
    private final Runnable probeListener;
    private final ProbeMarkerOverlay markers;
    private final Runnable removeFromPanes;
    private final WaveformsInFullScreen inFullScreen;
    private boolean disposed;

    public WaveformFeatures(EditorView view, TextPrompt prompt, BiConsumer<Component, String> messages) {
        this.view = view;
        this.messages = messages;
        OpenDocument openDocument = view.openDocument();
        BottomDock dock = view.bottomDock();
        this.actions = new ProbeActions(openDocument);
        Runnable repaintPanes = EveryPane.repaint(view);
        this.blink = new NetBlink(() -> view.canvas().scheme(), repaintPanes,
                () -> Animations.enabled(view.services().preferences()));
        this.panel = new WaveformPanel(openDocument, actions, () -> view.canvas().scheme(), prompt, this::showInCircuit,
                this::hidePanel);
        panel.setAnimations(() -> Animations.enabled(view.services().preferences()));
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
        Icons.install(toggle, WaveformIcons.PANEL); // the menu item's icon and the toolbar toggle's
        this.dockListener = () -> toggle.putValue(Action.SELECTED_KEY, dock.isDockShowing());
        dock.addDockListener(dockListener);
        dockListener.run();
        JCheckBoxMenuItem toggleItem = new JCheckBoxMenuItem(toggle);
        this.menuRemover = MenuSlots.add(view, MenuSlot.WAVEFORMS, toggleItem);
        this.shortcutRemover = view.registerShortcutAction("waveforms.toggle", toggle);
        view.addContextMenuContributor(new WaveformMenuContributor(actions, this::afterAdd));
        this.markers = new ProbeMarkerOverlay(openDocument, () -> view.canvas().scheme());
        this.removeFromPanes = view.forEachPane(this::installOn);
        this.probeListener = () -> {
            panel.probesChanged();
            probesChangedInFullScreen();
            repaintPanes.run(); // probe edits do not notify document listeners: the markers must repaint
        };
        openDocument.document().addProbeListener(probeListener);
        if (!openDocument.document().probes().isEmpty()) {
            dock.showDock();
        }
        this.inFullScreen = new WaveformsInFullScreen(view, panel, view.services().preferences());
        view.putClientProperty(WaveformFeatures.class, this);
    }

    /** The waveform feature of {@code view}; empty when the view has none (or it was disposed). */
    public static Optional<WaveformFeatures> of(EditorView view) {
        return view.getClientProperty(WaveformFeatures.class) instanceof WaveformFeatures features
                ? Optional.of(features) : Optional.empty();
    }

    /**
     * The panel shows {@code channels} instead of the document's probes (a machine tab's wave strip): Ver ▸ Formas de
     * onda, its «Ocultar», its pin and presentation mode's bottom edge then act on them. Returns what shows the probes
     * again.
     */
    public Runnable showChannels(WaveformChannels channels) {
        Runnable restorer = panel.showChannels(channels);
        channelsChanged();
        return () -> {
            restorer.run();
            channelsChanged();
        };
    }

    /** What the panel's rows are made of changed outside the probes (a machine tab entered another copy). */
    public void channelsChanged() {
        if (disposed) {
            return;
        }
        panel.probesChanged();
        probesChangedInFullScreen();
    }

    /**
     * {@code pin} is the pin of what the dock shows instead of the panel (a machine's test table): in presentation mode
     * it is shown and works exactly as the panel's own (docks it, remembered for every view). Returns what takes it
     * away.
     */
    public Runnable addPin(PinToggle pin) {
        return inFullScreen.addPin(pin);
    }

    /** The blink, the markers, their tooltip and their click in {@code pane}. Returns what undoes it. */
    private Runnable installOn(CircuitCanvas pane) {
        pane.addOverlay(blink);
        pane.addOverlay(markers);
        Runnable toolTipRemover = pane.addToolTipOverride(this::markerToolTip);
        Runnable pressRemover = pane.addPressInterceptor(event -> markerPressed(pane, event));
        return () -> uninstallFrom(pane, pressRemover, toolTipRemover);
    }

    private void uninstallFrom(CircuitCanvas pane, Runnable pressRemover, Runnable toolTipRemover) {
        pressRemover.run();
        toolTipRemover.run();
        pane.removeOverlay(markers);
        pane.removeOverlay(blink);
    }

    /** The panel's «Ocultar»: closes the dock, or in presentation mode takes the panel out of sight. */
    private void hidePanel() {
        if (disposed) {
            return;
        }
        inFullScreen.hide();
    }

    /** The document's probes changed; presentation mode's bottom edge follows them (shares the probe listener). */
    private void probesChangedInFullScreen() {
        if (!disposed && inFullScreen != null) {
            inFullScreen.probesChanged();
        }
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
     * channel of the marker and blinks its row. True consumes the press (the select tool never sees it). While a wire
     * is drawn the press is the wire's.
     */
    boolean markerPressed(CircuitCanvas pane, CanvasMouseEvent event) {
        if (disposed || !(pane.tool() instanceof SelectTool) || pane.drawsBetweenClicks()) {
            return false;
        }
        Optional<ProbeMarker> marker = markers.markerAt(event.gridPosition());
        if (marker.isEmpty()) {
            return false;
        }
        String probeId = marker.get().probeIds().getFirst();
        view.bottomDock().showDock();
        inFullScreen.reveal();
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
        inFullScreen.reveal();
        if (result.capReached()) {
            messages.accept(view, Messages.format("waveform.capReached", ProbeActions.MAXIMUM_CHANNELS));
        }
        if (!result.addedAny()) {
            result.existingId().ifPresent(panel::blinkRow);
        }
    }

    /**
     * «Mostrar en el circuito»: centers the active pane on the probe's net, selects its wires (or its components) and
     * blinks them; for a machine's state row, travels to the machine piece and selects it.
     */
    void showInCircuit(String probeId) {
        if (disposed) {
            return;
        }
        OpenDocument openDocument = view.openDocument();
        Optional<ElementId> machine = openDocument.document().probes().stream()
                .filter(probe -> probe.id().equals(probeId))
                .map(Probe::target)
                .flatMap(target -> target instanceof ProbeTarget.MachineStateTarget state
                        ? Stream.of(state.componentId())
                        : Stream.empty())
                .findFirst();
        if (machine.isPresent()) {
            // the state net lives only in the flat circuit: the row stands for the piece itself
            ProblemNavigator.focusComponent(view.canvas(), blink, machine.get());
            return;
        }
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
        view.putClientProperty(WaveformFeatures.class, null);
        inFullScreen.dispose();
        removeFromPanes.run();
        shortcutRemover.run();
        view.openDocument().document().removeProbeListener(probeListener);
        view.bottomDock().removeDockListener(dockListener);
        blink.dispose();
        panel.dispose();
        menuRemover.run();
        view.bottomDock().hideDock();
        view.bottomDock().setContent(null);
    }
}
