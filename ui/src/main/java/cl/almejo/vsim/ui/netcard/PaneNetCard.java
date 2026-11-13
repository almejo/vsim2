package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.window.EditorView;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The lit net of one pane: listens to the pointer there, runs the {@link NetHover} rules with a frame ticker while a
 * timeout is pending, and paints the {@link NetHalo}. Only the Selección tool at rest lights a net (no drag, no
 * rubber band, no wire being drawn, no floating text editor), and never over a pin, whose balloon stays.
 * <p>
 * The summary is built from the netlist and the latest snapshot on demand and kept until the document, the netlist
 * or the snapshot changes; when the wire the net was found by is deleted, the net goes out.
 */
final class PaneNetCard {

    private final OpenDocument openDocument;
    private final CircuitCanvas pane;
    private final NetCardFeatures.Setup setup;
    private final BooleanSupplier enabled;
    private final Consumer<PaneNetCard> lighting;
    private final NetHover<NetId> hover;
    private final FrameTicker ticker;
    private final ConnectionPoints connectionPoints;
    private final NetHalo halo;
    private final Pointer pointer = new Pointer();
    private final DocumentListener documentListener = (document, change) -> documentChanged();
    private final Consumer<SimulationSnapshot> snapshotListener = snapshot -> snapshotChanged();
    private final CircuitDocument listenedDocument;
    /** The last wire hovered on each net: the summary is found from it, also after the netlist is compiled again. */
    private final Map<NetId, ElementId> wireOfNet = new HashMap<>();
    private Cached cached;
    private float paintedOpacity;
    private boolean disposed;

    /** The summary built for one revision of the document, one netlist, one snapshot and one wire. */
    private record Cached(ElementId wire, long revision, long netlistToken, SimulationSnapshot snapshot,
                          Optional<NetSummary> summary) {
    }

    PaneNetCard(EditorView view, CircuitCanvas pane, NetCardFeatures.Setup setup, BooleanSupplier enabled,
                Consumer<PaneNetCard> lighting) {
        this.openDocument = view.openDocument();
        this.pane = Objects.requireNonNull(pane, "pane");
        this.setup = setup;
        this.enabled = enabled;
        this.lighting = lighting;
        this.hover = new NetHover<>(setup.clock(), setup.initialDelay());
        this.ticker = setup.tickers().get();
        this.connectionPoints = new ConnectionPoints(openDocument.registry());
        this.halo = new NetHalo(this::litNet);
        this.listenedDocument = openDocument.document();
        pane.addMouseListener(pointer);
        pane.addMouseMotionListener(pointer);
        pane.addOverlay(halo);
        pane.addSnapshotListener(snapshotListener);
        listenedDocument.addListener(documentListener);
    }

    /** The net lit now, with its value from the latest snapshot. */
    Optional<NetSummary> shown() {
        Optional<NetId> current = hover.current();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        ElementId wire = wireOfNet.get(current.get());
        return wire == null ? Optional.empty() : summaryOf(wire);
    }

    NetHalo halo() {
        return halo;
    }

    /** Puts the net out and drops the card, pinned or not. */
    void close() {
        boolean wasLit = hover.current().isPresent();
        hover.close();
        wireOfNet.clear();
        ticker.stop();
        if (wasLit) {
            pane.repaint();
        }
    }

    /** Another pane lit a net: this one's goes unless pinned. */
    void closeUnlessPinned() {
        if (!hover.pinned()) {
            close();
        }
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        close();
        pane.removeMouseListener(pointer);
        pane.removeMouseMotionListener(pointer);
        pane.removeOverlay(halo);
        pane.removeSnapshotListener(snapshotListener);
        listenedDocument.removeListener(documentListener);
    }

    private Optional<NetHalo.Shown> litNet() {
        return shown().map(summary -> new NetHalo.Shown(summary, hover.opacity()));
    }

    /** The pointer stands at ({@code x}, {@code y}) of the pane with no button held. */
    private void pointerAt(int x, int y) {
        if (!enabled.getAsBoolean()) {
            close();
            return;
        }
        Optional<Wire> wire = isIdle() ? wireAt(pane.viewport().screenToGrid(x, y)) : Optional.empty();
        Optional<Net> net = wire.flatMap(found -> openDocument.netlist().netForWire(found.id()));
        if (net.isEmpty()) {
            hover.offWire();
        } else {
            wireOfNet.put(net.get().id(), wire.get().id());
            hover.overWire(net.get().id());
            lighting.accept(this);
        }
        changed();
    }

    /** The wire under {@code gridPoint}; none over a pin, whose balloon stays. */
    private Optional<Wire> wireAt(Point2D gridPoint) {
        CircuitDocument document = openDocument.document();
        if (connectionPoints.pinNear(document, gridPoint).isPresent()) {
            return Optional.empty();
        }
        Optional<DocumentElement> hit = pane.hitTester().elementAt(document, gridPoint);
        return hit.filter(Wire.class::isInstance).map(Wire.class::cast);
    }

    /** The Selección tool with nothing pressed, dragged or drawn, and no text editor floating over the pane. */
    private boolean isIdle() {
        return pane.tool() instanceof SelectTool selectTool && !selectTool.autoscrolls()
                && !pane.drawsBetweenClicks() && !aChildFloatsOverThePane();
    }

    /** True while the pane holds a Swing child of its own (the inline text editor). */
    private boolean aChildFloatsOverThePane() {
        for (java.awt.Component child : pane.getComponents()) {
            if (child.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private void frame() {
        hover.tick();
        changed();
    }

    /** After any change of the rules: the ticker runs while a timeout is pending; the pane repaints what changed. */
    private void changed() {
        if (hover.needsTicks()) {
            if (!ticker.isRunning()) {
                ticker.start(this::frame);
            }
        } else if (ticker.isRunning()) {
            ticker.stop();
        }
        if (hover.current().isEmpty()) {
            wireOfNet.clear();
        }
        float opacity = hover.opacity();
        if (opacity != paintedOpacity) {
            paintedOpacity = opacity;
            pane.repaint();
        }
    }

    private void snapshotChanged() {
        if (hover.current().isPresent()) {
            pane.repaint();
        }
    }

    /** The wire the net was found by was deleted: the net goes out, pinned or not. */
    private void documentChanged() {
        Optional<NetId> current = hover.current();
        if (current.isEmpty()) {
            return;
        }
        ElementId wire = wireOfNet.get(current.get());
        if (wire == null || openDocument.document().wire(wire).isEmpty()) {
            close();
        }
    }

    private Optional<NetSummary> summaryOf(ElementId wire) {
        CircuitDocument document = openDocument.document();
        SimulationSnapshot snapshot = setup.snapshots().apply(openDocument);
        Cached last = cached;
        if (last != null && last.wire().equals(wire) && last.revision() == document.revision()
                && last.netlistToken() == openDocument.netlistToken() && last.snapshot() == snapshot) {
            return last.summary();
        }
        Optional<NetSummary> summary = NetSummaries.forWire(openDocument, wire, snapshot,
                () -> pane.highlights().topLevelNets(document, openDocument.netlist()));
        summary.ifPresent(found -> followRenamedNet(found.netId(), wire));
        cached = new Cached(wire, document.revision(), openDocument.netlistToken(), snapshot, summary);
        return summary;
    }

    /** The netlist was compiled again and the lit net has a new id: the rules follow it. */
    private void followRenamedNet(NetId netId, ElementId wire) {
        Optional<NetId> current = hover.current();
        if (current.isPresent() && !current.get().equals(netId)) {
            wireOfNet.remove(current.get());
            wireOfNet.put(netId, wire);
            hover.rekey(netId);
        }
    }

    /** The pointer over this pane. */
    private final class Pointer extends MouseAdapter {

        @Override
        public void mouseMoved(MouseEvent event) {
            pointerAt(event.getX(), event.getY());
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            closeUnlessPinned();
        }

        @Override
        public void mousePressed(MouseEvent event) {
            closeUnlessPinned();
        }

        @Override
        public void mouseExited(MouseEvent event) {
            hover.offWire();
            changed();
        }
    }
}
