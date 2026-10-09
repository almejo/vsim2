package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;

import java.awt.geom.Point2D;
import java.util.Optional;

/** What the canvas asks of the window around it. */
public interface CanvasHost {

    void showContextMenu(CircuitCanvas canvas, Optional<DocumentElement> target, Point2D screenPosition);

    void configure(Component component);

    /** Edits the label of a component, or the text of a free text label. Hosts without label editing ignore it. */
    default void editLabel(DocumentElement element) {
    }
}
