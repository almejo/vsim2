package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import java.awt.geom.Point2D;
import java.util.Optional;

/** What the canvas asks of the window around it. */
public interface CanvasHost {

    void showContextMenu(CircuitCanvas canvas, Optional<DocumentElement> target, Point2D screenPosition);

    void configure(Component component);

    /** Edits the label of a component, or the text of a free text label. Hosts without label editing ignore it. */
    default void editLabel(DocumentElement element) {
    }

    /** Opens the inline editor for a new free text at {@code point}. Hosts without label editing ignore it. */
    default void addTextAt(GridPoint point) {
    }

    /**
     * How moves made in the canvas treat the attached wires (M6.7); asked once, when the canvas is built. Hosts
     * without a window get the defaults and no notices.
     */
    default SelectionMoves selectionMoves() {
        return SelectionMoves.detached();
    }

    /** Edits in place the value of a Constante; hosts without in-place editing open the configure dialog. */
    default void editValue(Component component) {
        configure(component);
    }

    /** Opens the content editor of a RAM or ROM; hosts without one open the configure dialog. */
    default void editMemory(Component component) {
        configure(component);
    }

    /**
     * Whether {@code gridPosition} is on the ▾ / ▸ square of the memory {@code component} (a click there folds or
     * unfolds its content window); never in hosts without memories.
     */
    default boolean memoryFoldToggleAt(Component component, Point2D gridPosition) {
        return false;
    }

    /** M7: edits a bus input pin's value starting from {@code startingValue}; by default as {@link #editValue}. */
    default void editValue(Component component, Value startingValue) {
        editValue(component);
    }
}
