package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.paint.TextFonts;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

/**
 * Finds the sticky note, and the part of it, under a point given in (fractional) grid units. Notes are painted last,
 * in document order, so the last one holding the point is the one on top and the one hit. The parts come from the
 * same {@link StickyNoteLayout} the painter draws with, so a click lands where the user sees the ✕, a checkbox or the
 * folded corner. Kept apart from {@code HitTester}, which never sees sticky notes.
 */
public final class StickyNoteHitTester {

    /** Document pixels around a checkbox that still tick it: the box is small and drawn with a rounded stroke. */
    static final double CHECKBOX_TOLERANCE = 1.5;
    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;

    private StickyNoteHitTester() {
    }

    /**
     * The topmost note under {@code gridPoint} and its part. {@code pixelsPerGrid} is the screen size of a grid unit
     * (8 at 100 %): below the layout's detail threshold the ✕ and the checkboxes are not drawn, so they are not hit.
     */
    public static Optional<StickyNoteHit> hit(CircuitDocument document, Point2D gridPoint, double pixelsPerGrid,
                                              FontMetrics metrics) {
        double x = gridPoint.getX() * UNIT;
        double y = gridPoint.getY() * UNIT;
        List<StickyNote> notes = document.stickyNotes();
        for (int index = notes.size() - 1; index >= 0; index--) {
            StickyNote note = notes.get(index);
            if (StickyNoteLayout.paperOf(note).contains(x, y)) {
                return Optional.of(partAt(note, x, y, pixelsPerGrid, metrics));
            }
        }
        return Optional.empty();
    }

    private static StickyNoteHit partAt(StickyNote note, double x, double y, double pixelsPerGrid,
                                        FontMetrics metrics) {
        if (note.folded()) {
            return new StickyNoteHit(note.id(), StickyPart.BODY, StickyNoteHit.NO_ITEM);
        }
        StickyNoteLayout layout = StickyNoteLayout.of(note, metrics, pixelsPerGrid);
        boolean detailed = pixelsPerGrid >= StickyNoteLayout.MINIMUM_PIXELS_PER_GRID_FOR_DETAILS;
        if (detailed && layout.closeBox().contains(x, y)) {
            return new StickyNoteHit(note.id(), StickyPart.CLOSE, StickyNoteHit.NO_ITEM);
        }
        // the painter draws box n for item n (the boxes stop where the paper does), so the index is the item's
        for (int itemIndex = 0; itemIndex < layout.checkboxes().size(); itemIndex++) {
            Rectangle2D box = layout.checkboxes().get(itemIndex);
            Rectangle2D grabbed = new Rectangle2D.Double(box.getX() - CHECKBOX_TOLERANCE,
                    box.getY() - CHECKBOX_TOLERANCE, box.getWidth() + 2 * CHECKBOX_TOLERANCE,
                    box.getHeight() + 2 * CHECKBOX_TOLERANCE);
            if (grabbed.contains(x, y)) {
                return new StickyNoteHit(note.id(), StickyPart.CHECKBOX, itemIndex);
            }
        }
        if (layout.resizeHandle().contains(x, y)) {
            return new StickyNoteHit(note.id(), StickyPart.RESIZE, StickyNoteHit.NO_ITEM);
        }
        return new StickyNoteHit(note.id(), StickyPart.BODY, StickyNoteHit.NO_ITEM);
    }

    /**
     * Font metrics of the canvas text font, to lay notes out where no painting graphics is at hand (hit testing): the
     * painter measures with the same font, so the rows land on the same lines.
     */
    public static FontMetrics measuringMetrics() {
        Graphics2D scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            return scratch.getFontMetrics(TextFonts.labelFont());
        } finally {
            scratch.dispose();
        }
    }
}
