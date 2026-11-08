package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the canvas does with the mouse: select, place a component, draw a wire, pan. */
public interface Tool {

    default void activated(ToolContext context) {
    }

    default void deactivated(ToolContext context) {
    }

    /**
     * The canvas lost the keyboard focus or its window was deactivated while the tool may hold a press whose release
     * will never reach it (a push button held down). The default does nothing.
     */
    default void focusLost(ToolContext context) {
    }

    /**
     * True while the tool is in the middle of a gesture on the document's elements (a move, a wire, a band, a label
     * dragged, a button held) that Escape would cancel. A component tab whose body changed elsewhere cancels it then
     * (spec §10.4). False by default: placing from the palette and pasting keep their ghost.
     */
    default boolean holdsGesture() {
        return false;
    }

    /** True while the tool paints a marching-ants outline of its own (the canvas keeps its animation clock running). */
    default boolean animatesOutline() {
        return false;
    }

    /**
     * True while the tool holds a gesture (the button is down) that may go on over another pane of the window (split
     * views): the pressed canvas then gives the tool the pointer's grid point as the pane under it sees it (with that
     * pane's view and snapping), and every pane paints the tool's overlay. False by default: view gestures (panning)
     * and tools that work on press or hover stay in their pane.
     */
    default boolean crossesPanes() {
        return false;
    }

    /** True when the tool places what the palette has highlighted, so the palette keeps its highlight. */
    default boolean placesFromPalette() {
        return false;
    }

    default void mousePressed(ToolContext context, CanvasMouseEvent event) {
    }

    default void mouseDragged(ToolContext context, CanvasMouseEvent event) {
    }

    default void mouseReleased(ToolContext context, CanvasMouseEvent event) {
    }

    default void mouseMoved(ToolContext context, CanvasMouseEvent event) {
    }

    default void mouseExited(ToolContext context) {
    }

    /** @return true when the tool consumed the key, so canvas key bindings must not run */
    default boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        return false;
    }

    /**
     * Espacio was tapped (pressed and released with no pan in between) over the canvas: a tool drawing an «L» puts its
     * elbow on the other side; true when it did. The default does nothing.
     */
    default boolean flipElbow(ToolContext context) {
        return false;
    }

    /**
     * Deshacer was asked while the tool holds a gesture made of steps (the corners of a continuous wire): the tool
     * drops its last step instead of the history undoing an earlier change; true when it did. The default does
     * nothing, so the history undoes as usual.
     */
    default boolean dropLastStep(ToolContext context) {
        return false;
    }

    /**
     * Shift, Ctrl or Alt was pressed or released (the arguments are the keys held after the change). A tool whose
     * preview depends on the keys redoes it as if the pointer had moved; true when it did. The default does nothing.
     */
    default boolean modifiersChanged(ToolContext context, boolean shiftDown, boolean controlDown, boolean altDown) {
        return false;
    }

    /**
     * A snap preference, the viewport or the guides changed: a tool whose preview is snapped redoes it as if the
     * pointer had moved, so a release or Enter right after commits the refreshed result; true when it repainted a
     * changed preview. The default does nothing.
     */
    default boolean refreshSnap(ToolContext context) {
        return false;
    }

    /**
     * The guides the gesture under way lies on because Ajustar put it there or it matches them exactly (a moved box's
     * edge or centre, a wire or frame point): the guide overlay draws them highlighted (alignment-guides spec §4.4).
     * Empty when no gesture is under way; the default has none.
     */
    default List<Guide> snappedGuides() {
        return List.of();
    }

    /**
     * The elements the move under way drags, where its preview shows them (moved by the drag's delta; for a Ctrl
     * copy, where the copies would go); asked while painting by overlays that follow a drag (the print sheets' cut
     * outline). Read-only: nothing is committed until the release. Empty when no move is under way; the default has
     * none.
     */
    default List<DocumentElement> movingElements(ToolContext context) {
        return List.of();
    }

    /** Paints previews on top of the circuit, in document coordinates (8 px per grid unit). */
    default void paintOverlay(Graphics2D graphics, ToolContext context) {
    }

    /**
     * Wires the canvas leaves out of this frame because the tool's preview shows them elsewhere (M6.7: the wires a
     * dragged move stretches); asked before every paint.
     */
    default Set<ElementId> hiddenWires(ToolContext context) {
        return Set.of();
    }

    /**
     * Parts the canvas paints, in this frame, as the tool's preview has them instead of as the document has them, by
     * id (Task 6b: the Teclado at the width its tab is dragged to); asked before every paint. None by default.
     */
    default Map<ElementId, Component> previewedParts(ToolContext context) {
        return Map.of();
    }

    default Cursor cursor() {
        return Cursor.getDefaultCursor();
    }
}
