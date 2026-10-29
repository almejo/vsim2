package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.util.List;
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

    default Cursor cursor() {
        return Cursor.getDefaultCursor();
    }
}
