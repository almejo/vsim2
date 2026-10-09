package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.canvas.Selection;

import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.Optional;

/**
 * Adds items to the canvas context menu. Contributors run in registration order after the basic entries; each
 * non-empty group is separated from what precedes it. {@code target} is the element under the mouse, if any.
 */
@FunctionalInterface
public interface ContextMenuContributor {
    /** {@code gridPosition} is where the right click happened, in (fractional) grid units. */
    void contribute(JPopupMenu menu, Optional<DocumentElement> target, Selection selection, Point2D gridPosition);
}
