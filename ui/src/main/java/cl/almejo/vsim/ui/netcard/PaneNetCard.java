package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ConnectionPoints;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.window.InstanceNames;
import cl.almejo.vsim.ui.window.EditorView;
import com.formdev.flatlaf.util.UIScale;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The lit net of one pane and its signal card: listens to the pointer there, runs the {@link NetHover} rules with a
 * frame ticker while a timeout is pending, paints the {@link NetHalo} and puts the {@link SignalCard} over the pane
 * next to the pointer (it stays put, inside the pane, and fades with the grace). Only the Selección tool at rest
 * lights a net (no drag, no rubber band, no wire being drawn, no floating text editor), and never over a pin, whose
 * balloon stays.
 * <p>
 * A pinned card claims Esc ({@link CircuitCanvas#addEscapeClaim(BooleanSupplier, Runnable)}: the canvas closes it
 * before its tool sees the key, and presentation mode leaves it the key) and closes with it, with ✕ or with a press
 * on the pane outside it. Any card closes when the user is taken away from the view (its tab is hidden). The summary is built from the netlist and the latest
 * snapshot on demand and kept until the document, the netlist or the snapshot changes (the card follows the live
 * value); when the wire the net was found by is deleted, the net goes out and the card with it.
 */
final class PaneNetCard {

    /** How far from the pointer the card appears (pixels at 100 %), so the pointer is not over it. */
    private static final int POINTER_OFFSET = 14;
    /** The card keeps this far from the pane's edges (pixels at 100 %). */
    private static final int EDGE_MARGIN = 4;
    /** A part is taken beside the card only when that side keeps at least this share of the pane. */
    private static final double MINIMUM_FREE_SHARE = 0.3;

    private final EditorView view;
    private final OpenDocument openDocument;
    private final CircuitCanvas pane;
    private final NetCardFeatures.Setup setup;
    private final BooleanSupplier enabled;
    private final Consumer<PaneNetCard> lighting;
    private final NetHover<NetId> hover;
    private final FrameTicker ticker;
    private final ConnectionPoints connectionPoints;
    private final NetHalo halo;
    private final SignalCard card;
    private final Pointer pointer = new Pointer();
    private final DocumentListener documentListener = (document, change) -> documentChanged();
    private final Consumer<SimulationSnapshot> snapshotListener = snapshot -> snapshotArrived();
    private final Runnable removeEscapeClaim;
    private final Runnable removeTakenAwayListener;
    private final CircuitDocument listenedDocument;
    /** The last wire hovered on each net: the summary is found from it, also after the netlist is compiled again. */
    private final Map<NetId, ElementId> wireOfNet = new HashMap<>();
    private Cached cached;
    private CachedNames cachedNames;
    private float paintedOpacity;
    private Point lastPointer = new Point();
    /** Takes the card off the pane; null while it is not there. */
    private Runnable removeCard;
    /** The net the card was placed for: another net places it again, at the pointer. */
    private NetId placedFor;
    private boolean disposed;

    /**
     * The summary built for one revision of the document, one netlist, one wire and what a snapshot tells of its
     * net: a new snapshot (one per frame while running) that leaves the net as it was does not build it again.
     */
    private record Cached(ElementId wire, long revision, long netlistToken, SnapshotOfNet snapshot,
                          Optional<NetSummary> summary) {
    }

    /** What a summary reads from a snapshot: whether it is current, the value of the net and the problems. */
    private record SnapshotOfNet(long token, boolean empty, Optional<Value> value, List<Problem> problems) {

        static SnapshotOfNet of(SimulationSnapshot snapshot, Optional<Net> net) {
            return new SnapshotOfNet(snapshot.token(), snapshot == SimulationSnapshot.EMPTY,
                    net.map(found -> snapshot.netValues().get(found.id().value())), snapshot.problems());
        }
    }

    /** The names of the parts in one revision of the document («Interruptor 2»). */
    private record CachedNames(long revision, Map<ElementId, String> names) {
    }

    PaneNetCard(EditorView view, CircuitCanvas pane, NetCardFeatures.Setup setup, BooleanSupplier enabled,
                Consumer<PaneNetCard> lighting) {
        this.view = Objects.requireNonNull(view, "view");
        this.openDocument = view.openDocument();
        this.pane = Objects.requireNonNull(pane, "pane");
        this.setup = setup;
        this.enabled = enabled;
        this.lighting = lighting;
        this.hover = new NetHover<>(setup.clock(), setup.initialDelay());
        this.ticker = setup.tickers().get();
        this.connectionPoints = new ConnectionPoints(openDocument.registry());
        this.halo = new NetHalo(this::litNet);
        this.card = new SignalCard(new CardListener());
        this.listenedDocument = openDocument.document();
        pane.addMouseListener(pointer);
        pane.addMouseMotionListener(pointer);
        pane.addOverlay(halo);
        pane.addSnapshotListener(snapshotListener);
        // the canvas serves this Esc before its tool, so a pinned card closes and the selection stays
        removeEscapeClaim = pane.addEscapeClaim(hover::pinned, this::close);
        removeTakenAwayListener = view.addTakenAwayListener(this::close);
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

    /** The card while it is over the pane. */
    Optional<SignalCard> card() {
        return removeCard == null ? Optional.empty() : Optional.of(card);
    }

    /** A new snapshot was published: the halo and the card show its value. */
    void snapshotArrived() {
        if (hover.current().isPresent()) {
            pane.repaint();
            changed();
        }
    }

    /** Puts the net out and takes the card away, pinned or not. */
    void close() {
        boolean wasLit = hover.current().isPresent();
        hover.close();
        wireOfNet.clear();
        ticker.stop();
        takeTheCardAway();
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
        removeEscapeClaim.run();
        removeTakenAwayListener.run();
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
        lastPointer = new Point(x, y);
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
                && !pane.drawsBetweenClicks() && !anotherChildFloatsOverThePane();
    }

    /** True while the pane holds a Swing child other than the card (the inline text editor). */
    private boolean anotherChildFloatsOverThePane() {
        for (java.awt.Component child : pane.getComponents()) {
            if (child != card && child.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private void frame() {
        hover.tick();
        changed();
    }

    /**
     * After any change of the rules: the ticker runs while a timeout is pending; the card comes, goes or follows the
     * summary and the fade; the pane repaints the halo when its strength changed.
     */
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
        showOrHideTheCard();
        float opacity = hover.opacity();
        if (opacity != paintedOpacity) {
            paintedOpacity = opacity;
            pane.repaint();
        }
    }

    private void showOrHideTheCard() {
        Optional<NetSummary> summary = hover.cardShown() ? shown() : Optional.empty();
        if (summary.isEmpty()) {
            if (hover.current().isPresent() && hover.cardShown()) {
                close(); // the net is gone
            } else {
                takeTheCardAway();
            }
            return;
        }
        if (removeCard == null) {
            card.lookMayHaveChanged(); // a theme or zoom change while it was away is not told to it
        }
        card.show(summary.get(), hover.pinned());
        card.setOpacity(hover.opacity());
        if (removeCard == null) {
            removeCard = pane.addFloatingComponent(card);
        }
        NetId net = hover.current().orElseThrow();
        if (!net.equals(placedFor)) {
            placedFor = net;
            placeAt(lastPointer);
        } else {
            fitAt(card.getLocation());
        }
    }

    private void takeTheCardAway() {
        placedFor = null;
        card.forgetThePointer(); // Swing tells a removed card no exit
        if (removeCard != null) {
            Runnable remove = removeCard;
            removeCard = null;
            remove.run();
        }
    }

    /** Below and right of the pointer; above it or left of it when the pane ends there. */
    private void placeAt(Point pointerPoint) {
        Dimension size = card.getPreferredSize();
        int offset = UIScale.scale(POINTER_OFFSET);
        int margin = UIScale.scale(EDGE_MARGIN);
        int x = pointerPoint.x + offset;
        if (x + size.width > pane.getWidth() - margin) {
            x = pointerPoint.x - offset - size.width;
        }
        int y = pointerPoint.y + offset;
        if (y + size.height > pane.getHeight() - margin) {
            y = pointerPoint.y - offset - size.height;
        }
        fitAt(new Point(x, y));
    }

    /** At {@code location} with its preferred size, moved in so that it stays inside the pane. */
    private void fitAt(Point location) {
        Dimension size = card.getPreferredSize();
        int margin = UIScale.scale(EDGE_MARGIN);
        int x = Math.max(margin, Math.min(location.x, pane.getWidth() - size.width - margin));
        int y = Math.max(margin, Math.min(location.y, pane.getHeight() - size.height - margin));
        if (card.getX() != x || card.getY() != y || !card.getSize().equals(size)) {
            card.setBounds(x, y, size.width, size.height);
            card.validate();
            card.repaint();
        }
    }

    /** A part on the card was clicked: it is selected and the view travels to it, beside the card, and marks it. */
    private void goTo(NetEnd end) {
        Optional<Bounds> bounds = openDocument.document().component(end.componentId())
                .map(component -> openDocument.registry().typeOf(component).bounds(component));
        if (bounds.isEmpty()) {
            return;
        }
        view.selection().set(List.of(end.componentId()));
        pane.travelTo(bounds.get(), areaBesideTheCard());
        pane.glow(bounds.get());
        pane.requestFocusInWindow();
    }

    /** The side of the pane the card leaves free, or the whole pane when that side is too narrow. */
    private Rectangle2D areaBesideTheCard() {
        Rectangle2D whole = new Rectangle2D.Double(0, 0, pane.getWidth(), pane.getHeight());
        if (removeCard == null) {
            return whole;
        }
        int leftWidth = card.getX();
        int rightWidth = pane.getWidth() - card.getX() - card.getWidth();
        Rectangle2D beside = leftWidth >= rightWidth
                ? new Rectangle2D.Double(0, 0, leftWidth, pane.getHeight())
                : new Rectangle2D.Double(card.getX() + card.getWidth(), 0, rightWidth, pane.getHeight());
        return beside.getWidth() >= pane.getWidth() * MINIMUM_FREE_SHARE ? beside : whole;
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
        SnapshotOfNet ofNet = SnapshotOfNet.of(snapshot, openDocument.netlist().netForWire(wire));
        Cached last = cached;
        if (last != null && last.wire().equals(wire) && last.revision() == document.revision()
                && last.netlistToken() == openDocument.netlistToken() && last.snapshot().equals(ofNet)) {
            return last.summary();
        }
        Optional<NetSummary> summary = NetSummaries.forWire(openDocument, wire, snapshot,
                () -> pane.highlights().topLevelNets(document, openDocument.netlist()), this::partNames);
        summary.ifPresent(found -> followRenamedNet(found.netId(), wire));
        cached = new Cached(wire, document.revision(), openDocument.netlistToken(), ofNet, summary);
        return summary;
    }

    private Map<ElementId, String> partNames() {
        CircuitDocument document = openDocument.document();
        CachedNames last = cachedNames;
        if (last != null && last.revision() == document.revision()) {
            return last.names();
        }
        Map<ElementId, String> names = InstanceNames.names(document.components(), document);
        cachedNames = new CachedNames(document.revision(), names);
        return names;
    }

    /** The netlist was compiled again and the lit net has a new id: the rules follow it. */
    private void followRenamedNet(NetId netId, ElementId wire) {
        Optional<NetId> current = hover.current();
        if (current.isPresent() && !current.get().equals(netId)) {
            wireOfNet.remove(current.get());
            wireOfNet.put(netId, wire);
            hover.rekey(netId);
            if (current.get().equals(placedFor)) {
                placedFor = netId;
            }
        }
    }

    /** The pointer over this pane (not over the card, which takes its own events). */
    private final class Pointer extends MouseAdapter {

        @Override
        public void mouseMoved(MouseEvent event) {
            pointerAt(event.getX(), event.getY());
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            closeUnlessPinned();
        }

        /** A press anywhere on the pane closes the card, pinned or not: a click outside it. */
        @Override
        public void mousePressed(MouseEvent event) {
            close();
        }

        @Override
        public void mouseExited(MouseEvent event) {
            hover.offWire();
            changed();
        }
    }

    /** What the card asks for. */
    private final class CardListener implements SignalCard.Listener {

        @Override
        public void pinPressed() {
            hover.pin();
            changed();
        }

        @Override
        public void closePressed() {
            close();
        }

        @Override
        public void endPressed(NetEnd end) {
            goTo(end);
        }

        @Override
        public void pointerEntered() {
            hover.enterCard();
            changed();
        }

        @Override
        public void pointerLeft() {
            hover.leaveCard();
            changed();
        }
    }
}
