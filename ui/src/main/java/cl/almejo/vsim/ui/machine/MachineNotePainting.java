package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.canvas.MarchingAnts;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.sticky.StickyNoteLayout;
import cl.almejo.vsim.ui.sticky.StickyNotePainter;
import cl.almejo.vsim.ui.sticky.StickyNotesExtension;
import cl.almejo.vsim.ui.sticky.StickyPart;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;

/**
 * Paints a machine's diagram notes as the circuit's sticky notes ({@link StickyNotePainter}: the same paper, fold,
 * shadow, colours and wrapped text), and says where they are for the gestures. A diagram note is in diagram units
 * (pixels at 100 %), a circuit note in grid units of {@link #UNIT} pixels: the note is painted as a transient circuit
 * note at the origin, of its size in whole units, with the graphics moved to the note's top-left corner. Sizes are kept
 * in whole units by the edits ({@link MachineEdits#withNoteResized}); a size read from a file that is not is rounded
 * the same way here and in the hit tests, so what is seen is what is clicked. The circuit note's ✕ and fold button «–»
 * are drawn and hit from the same {@link StickyNoteLayout} as the circuit's ({@link #partAt}), and a folded note is the
 * circuit's small paper of {@link StickyNote#FOLDED_SIZE} units.
 */
final class MachineNotePainting {

    /** Pixels per grid unit of the circuit's notes. */
    static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    /** The resize corner, on screen, at least: it stays easy to grab when zoomed out. */
    static final double RESIZE_CORNER_PIXELS = StickyNoteLayout.RESIZE_SIZE;
    private static final float SELECTION_WIDTH = 2f;
    private static final double SELECTION_MARGIN = 3;
    private static final PaintPolicy POLICY = PaintPolicy.CANVAS;

    private MachineNotePainting() {
    }

    /** The part of a note under a point: the note and which of its parts ({@link StickyPart}). */
    record NoteHit(MachineNote note, StickyPart part) {
    }

    /** The paper of {@code note} as it is painted and hit, in diagram units (the small paper when it is folded). */
    static Rectangle2D paperOf(MachineNote note) {
        Rectangle2D paper = StickyNoteLayout.paperOf(asStickyNote(note));
        return new Rectangle2D.Double(note.position().x(), note.position().y(), paper.getWidth(), paper.getHeight());
    }

    /**
     * Paints {@code note} in diagram units at {@code zoom} (the graphics already carries the view's transform), with
     * the circuit note's ✕ and «–»; {@code closeHovered} draws the ✕ at full strength (the pointer is on it).
     */
    static void paint(Graphics2D graphics, MachineNote note, ColorScheme scheme, double zoom, boolean closeHovered) {
        Graphics2D moved = (Graphics2D) graphics.create();
        try {
            moved.translate(note.position().x(), note.position().y());
            StickyNotePainter.paint(moved, asStickyNote(note), scheme, zoom * UNIT, POLICY, closeHovered);
        } finally {
            moved.dispose();
        }
    }

    /**
     * The topmost note of {@code machine} under {@code point} and its part, from the layout the painter draws with at
     * {@code zoom}: the ✕ ({@link StickyPart#CLOSE}) and the «–» ({@link StickyPart#FOLD}) of an unfolded note where
     * they are drawn (not when zoomed out so far that they are not), else {@link StickyPart#BODY}. The resize corner
     * is the caller's ({@link #onResizeCorner}), which keeps it easy to grab when zoomed out.
     */
    static Optional<NoteHit> partAt(StateMachine machine, Point2D point, double zoom) {
        return noteAt(machine, point).map(note -> new NoteHit(note, partOf(note, point, zoom)));
    }

    private static StickyPart partOf(MachineNote note, Point2D point, double zoom) {
        double pixelsPerGrid = zoom * UNIT;
        if (note.folded() || pixelsPerGrid < StickyNoteLayout.MINIMUM_PIXELS_PER_GRID_FOR_DETAILS) {
            return StickyPart.BODY;
        }
        StickyNoteLayout layout = StickyNoteLayout.of(asStickyNote(note), pixelsPerGrid);
        double x = point.getX() - note.position().x();
        double y = point.getY() - note.position().y();
        if (layout.closeBox().contains(x, y)) {
            return StickyPart.CLOSE;
        }
        return layout.foldButton().contains(x, y) ? StickyPart.FOLD : StickyPart.BODY;
    }

    /** The text of the folded note of {@code machine} under {@code point}, as a circuit's folded note shows it. */
    static Optional<String> foldedToolTipAt(StateMachine machine, Point2D point) {
        return noteAt(machine, point).map(MachineNotePainting::asStickyNote)
                .flatMap(StickyNotesExtension::foldedNoteToolTip);
    }

    /** {@code note} as a transient circuit note at the origin, of its size in whole units: what is painted. */
    private static StickyNote asStickyNote(MachineNote note) {
        return new StickyNote(new ElementId(note.id()), GridPoint.ORIGIN, unitsOf(note.width()),
                unitsOf(note.height()), note.color(), new StickyText(note.text()), note.folded());
    }

    /** The marching ants of a selected note, a little outside its paper, in {@code MachineColors.selection}. */
    static void paintSelection(Graphics2D graphics, MachineNote note, ColorScheme scheme, double antsPhase) {
        Rectangle2D paper = paperOf(note);
        MarchingAnts.draw(graphics, new Rectangle2D.Double(paper.getX() - SELECTION_MARGIN,
                paper.getY() - SELECTION_MARGIN, paper.getWidth() + 2 * SELECTION_MARGIN,
                paper.getHeight() + 2 * SELECTION_MARGIN), MachineColors.selection(scheme), SELECTION_WIDTH, antsPhase);
    }

    /** The topmost note of {@code machine} under {@code point} (the last painted wins); empty over none. */
    static Optional<MachineNote> noteAt(StateMachine machine, Point2D point) {
        List<MachineNote> notes = machine.notes();
        for (int index = notes.size() - 1; index >= 0; index--) {
            if (paperOf(notes.get(index)).contains(point)) {
                return Optional.of(notes.get(index));
            }
        }
        return Optional.empty();
    }

    /**
     * Whether {@code point} is on the resize corner of {@code note}: its bottom-right square, {@code corner} diagram
     * units wide (the caller gives at least {@link #RESIZE_CORNER_PIXELS} on screen). A folded note has none.
     */
    static boolean onResizeCorner(MachineNote note, Point2D point, double corner) {
        if (note.folded()) {
            return false;
        }
        Rectangle2D paper = paperOf(note);
        double size = Math.min(corner, Math.min(paper.getWidth(), paper.getHeight()) / 2);
        return new Rectangle2D.Double(paper.getMaxX() - size, paper.getMaxY() - size, size, size).contains(point);
    }

    /** Where the text of {@code note} is edited: its paper less the padding, in diagram units. */
    static Rectangle2D textArea(MachineNote note) {
        Rectangle2D paper = paperOf(note);
        double padding = StickyNoteLayout.PADDING;
        return new Rectangle2D.Double(paper.getX() + padding, paper.getY() + padding,
                paper.getWidth() - 2 * padding, paper.getHeight() - 2 * padding);
    }

    /** The paper of every note of {@code machine} together; empty without notes. */
    static Optional<Rectangle2D> bounds(StateMachine machine) {
        Rectangle2D union = null;
        for (MachineNote note : machine.notes()) {
            Rectangle2D paper = paperOf(note);
            union = union == null ? paper : union.createUnion(paper);
        }
        return Optional.ofNullable(union);
    }

    /** {@code length} diagram units in whole note units (at least one). */
    private static int unitsOf(double length) {
        return Math.max(1, (int) Math.round(length / UNIT));
    }
}
