package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
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
}
