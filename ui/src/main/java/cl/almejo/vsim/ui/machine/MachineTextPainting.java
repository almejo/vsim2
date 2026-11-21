package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineText;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import cl.almejo.vsim.ui.canvas.MarchingAnts;
import cl.almejo.vsim.ui.canvas.NotePainter;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;

/**
 * Paints a machine's free texts as the circuit's («Texto»: {@link NotePainter}, the same font, box, colours and
 * lines), and says where they are for the gestures. A diagram text is in diagram units (pixels at 100 %) and the
 * circuit's font is fixed in pixels, so the text is painted as a transient circuit text at the origin with the graphics
 * moved to its top-left corner: what is seen is what is clicked ({@link #box}). The selection is the circuit's marching
 * ants around the box.
 */
final class MachineTextPainting {

    private static final PaintPolicy POLICY = PaintPolicy.CANVAS;
    private static final float SELECTION_WIDTH = 1.5f;
    private static final double SELECTION_GAP = 3;

    private MachineTextPainting() {
    }

    /** {@code text} as a circuit text at the origin: what the layout and the painter measure and draw. */
    private static TextLabel asLabel(MachineText text) {
        return new TextLabel(new ElementId(text.id()), GridPoint.ORIGIN, text.text(), text.style());
    }

    /** The box {@code text} takes (its lines, and its padding when it has a background or a border), in diagram units. */
    static Rectangle2D box(MachineText text) {
        Rectangle2D box = LabelLayout.noteBox(asLabel(text));
        return new Rectangle2D.Double(box.getX() + text.position().x(), box.getY() + text.position().y(),
                box.getWidth(), box.getHeight());
    }

    /** Where the lines of {@code text} are drawn (and edited), in diagram units: its box less the padding. */
    static Rectangle2D linesArea(MachineText text) {
        Rectangle2D lines = LabelLayout.freeText(asLabel(text)).bounds();
        return new Rectangle2D.Double(lines.getX() + text.position().x(), lines.getY() + text.position().y(),
                lines.getWidth(), lines.getHeight());
    }

    /** Where the lines of a text about to be written with its top-left corner at ({@code x}, {@code y}) would be. */
    static Rectangle2D emptyLinesAt(double x, double y) {
        return linesArea(new MachineText("probe", new DiagramPoint(x, y), ""));
    }

    /** Paints {@code text} in diagram units (the graphics already carries the view's transform). */
    static void paint(Graphics2D graphics, MachineText text, ColorScheme scheme) {
        Graphics2D moved = (Graphics2D) graphics.create();
        try {
            moved.translate(text.position().x(), text.position().y());
            NotePainter.paint(moved, asLabel(text), scheme, POLICY);
        } finally {
            moved.dispose();
        }
    }

    /** The marching ants of a selected text, a little outside its box, in the diagram's selection colour. */
    static void paintSelection(Graphics2D graphics, MachineText text, ColorScheme scheme, double antsPhase) {
        Rectangle2D box = box(text);
        MarchingAnts.draw(graphics, new Rectangle2D.Double(box.getX() - SELECTION_GAP, box.getY() - SELECTION_GAP,
                box.getWidth() + 2 * SELECTION_GAP, box.getHeight() + 2 * SELECTION_GAP),
                MachineColors.selection(scheme), SELECTION_WIDTH, antsPhase);
    }

    /** The topmost text of {@code machine} under {@code point} (the last painted wins); empty over none. */
    static Optional<MachineText> textAt(StateMachine machine, Point2D point) {
        List<MachineText> texts = machine.texts();
        for (int index = texts.size() - 1; index >= 0; index--) {
            if (box(texts.get(index)).contains(point)) {
                return Optional.of(texts.get(index));
            }
        }
        return Optional.empty();
    }

    /** The box of every text of {@code machine} together; empty without texts. */
    static Optional<Rectangle2D> bounds(StateMachine machine) {
        Rectangle2D union = null;
        for (MachineText text : machine.texts()) {
            Rectangle2D box = box(text);
            union = union == null ? box : union.createUnion(box);
        }
        return Optional.ofNullable(union);
    }
}
