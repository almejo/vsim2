package cl.almejo.vsim.ui.tunnel;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.DocumentListener;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.ContextMenuContributor;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasOverlay;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import cl.almejo.vsim.ui.paint.TunnelPainter;
import cl.almejo.vsim.ui.panels.StatusBar;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What a view adds for wire tunnels (spec §7): hovering a tunnel lights every tunnel of its name (an outline in its
 * colour) and the status bar counts them («CLK: 1 entrada, 3 salidas»); the context menu of a tunnel offers its name
 * («Nombre…»), «Ir a la entrada» (on a salida) and «Siguiente salida» (cycling), which travel there; a tunnel without direction has
 * neither but «Siguiente túnel», which cycles through every tunnel of the name. A name with tunnels without direction
 * counts them apart in the hint. {@link #dispose}
 * takes back the listeners, the overlay and the menu items.
 */
public final class TunnelFeatures {

    /** The outline sits this far outside the tunnel's box, in document pixels. */
    private static final double OUTLINE_GAP = 3;
    private static final double OUTLINE_CORNER = 10;
    private static final BasicStroke OUTLINE = new BasicStroke(2f);

    private final EditorView view;
    private final OpenDocument openDocument;
    private final StatusBar statusBar;
    private final DocumentListener documentListener = this::documentChanged;
    private final ContextMenuContributor contributor = this::contribute;
    private final Runnable contributorRemover;
    private final Runnable paneInstallation;
    private Optional<ElementId> hovered = Optional.empty();

    public TunnelFeatures(EditorView view) {
        this.view = view;
        this.openDocument = view.openDocument();
        this.statusBar = view.statusBar();
        openDocument.document().addListener(documentListener);
        contributorRemover = view.addContextMenuContributor(contributor);
        paneInstallation = view.forEachPane(this::installOn);
    }

    /** On one pane: the pointer that hovers there and the outline, painted in every pane whichever one is hovered. */
    private Runnable installOn(CircuitCanvas pane) {
        PanePointer pointer = new PanePointer(pane);
        CanvasOverlay overlay = (graphics, state) -> paintHighlight(pane, graphics, state);
        pane.addMouseMotionListener(pointer);
        pane.addMouseListener(pointer);
        pane.addOverlay(overlay);
        return () -> {
            pane.removeMouseMotionListener(pointer);
            pane.removeMouseListener(pointer);
            pane.removeOverlay(overlay);
        };
    }

    public void dispose() {
        paneInstallation.run();
        openDocument.document().removeListener(documentListener);
        contributorRemover.run();
        hover(Optional.empty());
    }

    /** The pointer is at {@code gridPoint} over the active pane: lights the tunnel there and its name, or nothing. */
    void hoverAt(Point2D gridPoint) {
        hoverAt(view.canvas(), gridPoint);
    }

    /** The pointer is at {@code gridPoint} over {@code pane}: lights the tunnel there and its name, or nothing. */
    private void hoverAt(CircuitCanvas pane, Point2D gridPoint) {
        hover(pane.hitTester().elementAt(openDocument.document(), gridPoint)
                .filter(element -> element instanceof Component component && Tunnels.isTunnel(component.typeId()))
                .map(DocumentElement::id));
    }

    /** The name of the tunnel under the pointer. */
    Optional<String> hoveredName() {
        return hoveredTunnel().map(Tunnels::nameOf);
    }

    /** Every tunnel lit now: those of the hovered name. */
    Set<ElementId> highlighted() {
        return hoveredName().map(this::tunnelsNamed).map(Set::copyOf).orElse(Set.of());
    }

    ContextMenuContributor contributor() {
        return contributor;
    }

    private Optional<Component> hoveredTunnel() {
        return hovered.flatMap(id -> openDocument.document().component(id))
                .filter(component -> Tunnels.isTunnel(component.typeId()));
    }

    private void hover(Optional<ElementId> tunnel) {
        boolean changed = !tunnel.equals(hovered);
        hovered = tunnel;
        showHint();
        if (changed) {
            view.panes().forEach(CircuitCanvas::repaint);
        }
    }

    /** A rename or a removal changes what the hovered tunnel stands for. */
    private void documentChanged(CircuitDocument document, DocumentChange change) {
        if (hovered.isPresent()) {
            hover(hoveredTunnel().map(Component::id));
        }
    }

    private void showHint() {
        statusBar.showHint(hoveredName().map(this::hint).orElse(""));
    }

    /**
     * «CLK: 1 entrada, 3 salidas» for tunnels with direction, «CLK: 4 túneles sin dirección» for the ones without,
     * and both lists joined for a name that mixes them.
     */
    private String hint(String name) {
        Tunnels.TunnelGroup group = Tunnels.groups(openDocument.document()).get(name);
        int entrances = group == null ? 0 : group.directedEntrances().size();
        int exits = group == null ? 0 : group.directedExits().size();
        int undirected = group == null ? 0 : group.undirected().size();
        if (undirected == 0) {
            return Messages.format("tunnel.hover", name, entrances, exits);
        }
        if (entrances + exits == 0) {
            return Messages.format("tunnel.hover.undirected", name, undirected);
        }
        return Messages.format("tunnel.hover.mixed", name, entrances, exits, undirected);
    }

    private List<ElementId> tunnelsNamed(String name) {
        Tunnels.TunnelGroup group = Tunnels.groups(openDocument.document()).get(name);
        if (group == null) {
            return List.of();
        }
        return Stream.concat(group.entrances().stream(), group.exits().stream()).toList();
    }

    /** An outline in the name's colour (grey without entrada) around every tunnel of the hovered name. */
    private void paintHighlight(CircuitCanvas pane, Graphics2D graphics, RenderState state) {
        Set<ElementId> lit = highlighted();
        if (lit.isEmpty()) {
            return;
        }
        ColorScheme scheme = pane.scheme();
        Map<String, Integer> colors = Tunnels.colorsByName(state.document());
        graphics.setStroke(OUTLINE);
        for (ElementId id : lit) {
            state.document().component(id).ifPresent(tunnel -> {
                Rectangle2D bounds = ComponentDrawing.documentBounds(openDocument.registry().typeOf(tunnel), tunnel);
                graphics.setColor(scheme.tunnelColor(TunnelPainter.colorIndex(tunnel, colors)));
                graphics.draw(new RoundRectangle2D.Double(bounds.getX() - OUTLINE_GAP, bounds.getY() - OUTLINE_GAP,
                        bounds.getWidth() + 2 * OUTLINE_GAP, bounds.getHeight() + 2 * OUTLINE_GAP, OUTLINE_CORNER,
                        OUTLINE_CORNER));
            });
        }
    }

    private void contribute(JPopupMenu menu, Optional<DocumentElement> target, Selection selection,
                            Point2D gridPosition) {
        if (target.isEmpty() || !(target.get() instanceof Component tunnel) || !Tunnels.isTunnel(tunnel.typeId())) {
            return;
        }
        menu.add(item("tunnel.rename", () -> view.labelFeatures().actions().editValue(tunnel)));
        Tunnels.TunnelGroup group = Tunnels.groups(openDocument.document()).get(Tunnels.nameOf(tunnel));
        if (Tunnels.isUndirected(tunnel)) {
            nextTunnel(group, tunnel.id()).ifPresent(next -> menu.add(item("tunnel.nextTunnel", () -> travelTo(next))));
            return;
        }
        boolean exit = Tunnels.kindOf(tunnel).orElseThrow() == TunnelKind.EXIT;
        if (exit && !group.directedEntrances().isEmpty()) {
            ElementId entrance = group.directedEntrances().getFirst();
            menu.add(item("tunnel.goToEntrance", () -> travelTo(entrance)));
        }
        nextExit(group, tunnel.id()).ifPresent(next -> menu.add(item("tunnel.nextExit", () -> travelTo(next))));
    }

    /**
     * The salida with direction after {@code from} among its name's, in document order and wrapping around (the
     * first one from an entrada); empty when there is no other one to go to.
     */
    private static Optional<ElementId> nextExit(Tunnels.TunnelGroup group, ElementId from) {
        List<ElementId> exits = group.directedExits();
        int index = exits.indexOf(from);
        if (index < 0) {
            return exits.isEmpty() ? Optional.empty() : Optional.of(exits.getFirst());
        }
        return exits.size() < 2 ? Optional.empty() : Optional.of(exits.get((index + 1) % exits.size()));
    }

    /** The tunnel after {@code from} among all of its name, whatever their direction; empty when it is alone. */
    private static Optional<ElementId> nextTunnel(Tunnels.TunnelGroup group, ElementId from) {
        List<ElementId> all = Stream.concat(group.entrances().stream(), group.exits().stream()).toList();
        int index = all.indexOf(from);
        return all.size() < 2 || index < 0 ? Optional.empty() : Optional.of(all.get((index + 1) % all.size()));
    }

    /** Selects tunnel {@code id} and takes the active pane there with the smooth travel. */
    private void travelTo(ElementId id) {
        openDocument.document().component(id).ifPresent(tunnel -> {
            view.selection().select(id);
            view.canvas().travelTo(openDocument.registry().typeOf(tunnel).bounds(tunnel));
        });
    }

    /** Hovering over one pane. */
    private final class PanePointer extends MouseAdapter {

        private final CircuitCanvas pane;

        PanePointer(CircuitCanvas pane) {
            this.pane = pane;
        }

        @Override
        public void mouseMoved(MouseEvent event) {
            hoverAt(pane, pane.viewport().screenToGrid(event.getX(), event.getY()));
        }

        @Override
        public void mouseExited(MouseEvent event) {
            hover(Optional.empty());
        }
    }

    private static JMenuItem item(String key, Runnable behavior) {
        return new JMenuItem(Actions.of(Messages.text(key), behavior));
    }
}
