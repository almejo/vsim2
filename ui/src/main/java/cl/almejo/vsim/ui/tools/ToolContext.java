package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.HitTester;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.canvas.ViewState;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.snap.Snapper;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.Optional;

/** Everything a tool may use; implemented by the canvas and by a fake in tests. */
public interface ToolContext {

    OpenDocument openDocument();

    Selection selection();

    HitTester hitTester();

    Viewport viewport();

    /** The size in pixels of the pane the tool works in; by default none (0 × 0). */
    default Dimension viewSize() {
        return new Dimension();
    }

    /**
     * Takes the pane's view to {@code target} (its zoom clamped to the viewport's limits) as a zoom the user asked for
     * (the zoom tool's rectangle). The canvas travels there when view moves are animated; by default it jumps.
     */
    default void zoomTo(ViewState target) {
        Dimension size = viewSize();
        viewport().showAt(target.zoom(), target.centerX(), target.centerY(), size.width, size.height);
    }

    /**
     * Zooms the pane at once to {@code zoom} (clamped) around the screen point {@code screenPoint}, which keeps the
     * grid point under it (the zoom tool's click steps); the canvas also tells its view listeners (the status bar).
     */
    default void zoomAt(Point2D screenPoint, double zoom) {
        viewport().zoomAt(screenPoint.getX(), screenPoint.getY(), zoom);
    }

    ColorScheme scheme();

    /** How moves of the selection treat the attached wires (M6.7), and where their notices go. */
    SelectionMoves selectionMoves();

    /** Snaps the tool's gestures to parts, guides and grid lines; by default nothing snaps. */
    default Snapper snapper() {
        return Snapper.none();
    }

    /** The current dash phase of marching-ants outlines. */
    double selectionDashPhase();

    /** Paints {@code element} semi-transparent (drag and placement previews). */
    void paintGhost(Graphics2D graphics, DocumentElement element);

    /** Like {@link #paintGhost(Graphics2D, DocumentElement)}, resolving component types with {@code registry}. */
    default void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry registry) {
        paintGhost(graphics, element);
    }

    /**
     * Like {@link #paintGhost(Graphics2D, DocumentElement, ComponentTypeRegistry)}, a salida taking the colour of its
     * name in {@code tunnelColors} (palette colour 0 for a name without one): for a tool whose ghosts join the circuit
     * with entradas of their own (a paste, see {@link cl.almejo.vsim.core.tunnel.Tunnels#colorsByName(
     * cl.almejo.vsim.core.document.CircuitDocument, java.util.Collection)}).
     */
    default void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry registry,
                            Map<String, Integer> tunnelColors) {
        paintGhost(graphics, element, registry);
    }

    /**
     * A component of {@code type} chosen in the palette's world (a palette entry, a drop from the palette, a quick
     * insert) just landed in the circuit; pastes, duplicates, undo and the AI do not call this. By default nothing
     * listens (the canvas tells «Recientes»).
     */
    default void componentPlaced(ComponentType type) {
    }

    void repaint();

    void setTool(Tool tool);

    void useDefaultTool();

    /** Shows {@code cursor} over the canvas; while the view is being panned the canvas keeps MOVE and shows it after. */
    void setCursor(CanvasCursor cursor);

    void showContextMenu(Optional<DocumentElement> target, Point2D screenPosition);

    void configure(Component component);

    /** Edits the label of a component, or the text of a free text label. */
    void editLabel(DocumentElement element);

    /** Edits in place the value of a Constante; by default what a double click did before: the configure dialog. */
    default void editValue(Component component) {
        configure(component);
    }

    /** Opens the content editor of a RAM or ROM; by default (hosts without one) the configure dialog. */
    default void editMemory(Component component) {
        configure(component);
    }

    /**
     * M7: edits the value of a bus input pin starting from {@code startingValue} instead of the live one (a double
     * click puts back the value its first click toggled).
     */
    default void editValue(Component component, Value startingValue) {
        editValue(component);
    }
}
