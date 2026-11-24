package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.canvas.Selection;

/**
 * What a press on an element does to the selection at once, and what it leaves for a release without moving (a
 * click), shared by the select tool and the drags of frames and sticky notes ({@link ElementDrag}): a plain press
 * selects an unselected element, or keeps a multi-selection that holds it and narrows it to the element at the
 * release; a Shift press adds an unselected element, or takes a selected one out at the release (a Shift drag moves
 * the selection with it instead). A release after a drag leaves the selection as it is: the caller asks only after a
 * click.
 */
public final class SelectionOnRelease {

    private boolean narrows;
    private boolean removes;

    /**
     * The plain press on the element {@code id}: an unselected element becomes the selection; a selected one stays
     * with the others, and a release without moving narrows the selection to it.
     */
    public void selectOnPress(Selection selection, ElementId id) {
        if (selection.contains(id)) {
            narrows = selection.size() > 1;
        } else {
            selection.select(id);
        }
    }

    /**
     * The Shift press on the element {@code id}: a selected element leaves the selection at a release without moving,
     * an unselected one joins it now.
     */
    public void toggleOnPress(Selection selection, ElementId id) {
        if (selection.contains(id)) {
            removes = true;
        } else {
            selection.add(id);
        }
    }

    /** The release without moving of the press on the element {@code id}: takes it out, or narrows to it. */
    public void released(Selection selection, ElementId id) {
        if (removes) {
            selection.remove(id);
        } else if (narrows) {
            selection.select(id);
        }
    }

    /** Forgets the press. */
    public void reset() {
        narrows = false;
        removes = false;
    }
}
