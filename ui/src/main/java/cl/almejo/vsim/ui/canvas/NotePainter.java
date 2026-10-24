package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;

/**
 * Paints a note (a {@link TextLabel}): with a background or a border, a rounded box around its text
 * ({@link LabelLayout#noteBox}) filled with the background and outlined by a 1 px border; then its lines in its text
 * colour. A plain note is only its lines in the scheme's text colour, as ever. In black and white the background is
 * left out and the border and the text are black, so the note reads on paper whatever its colours.
 */
final class NotePainter {

    /** The diameter of the box's rounded corners, in document pixels. */
    private static final double CORNER = 6;
    private static final BasicStroke BORDER = new BasicStroke(1f);

    private NotePainter() {
    }

    /** Paints {@code note} in {@code scheme} under {@code policy}. */
    static void paint(Graphics2D graphics, TextLabel note, ColorScheme scheme, PaintPolicy policy) {
        NoteStyle style = note.style();
        if (style.boxed()) {
            paintBox(graphics, note, scheme, policy);
        }
        paintLines(graphics, note, policy.monochrome() ? scheme.text() : scheme.noteText(style));
    }

    /** The rounded outline of {@code note}'s box in {@code color}, 1 px (its ghost). */
    static void paintOutline(Graphics2D graphics, TextLabel note, Color color) {
        graphics.setStroke(BORDER);
        graphics.setColor(color);
        graphics.draw(box(note));
    }

    private static RoundRectangle2D box(TextLabel note) {
        Rectangle2D bounds = LabelLayout.noteBox(note);
        return new RoundRectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), CORNER,
                CORNER);
    }

    private static void paintBox(Graphics2D graphics, TextLabel note, ColorScheme scheme, PaintPolicy policy) {
        RoundRectangle2D box = box(note);
        NoteStyle style = note.style();
        if (!policy.monochrome()) {
            scheme.noteBackground(style).ifPresent(fill -> {
                graphics.setColor(fill);
                graphics.fill(box);
            });
        }
        Optional<Color> border = policy.monochrome()
                ? Optional.of(scheme.text()).filter(black -> style.border() != NoteColor.NONE)
                : scheme.noteOutline(style);
        border.ifPresent(color -> {
            graphics.setStroke(policy.widened(BORDER));
            graphics.setColor(color);
            graphics.draw(box);
        });
    }

    /** Every line of {@code note} in {@code color}, each at its own baseline. */
    static void paintLines(Graphics2D graphics, TextLabel note, Color color) {
        LabelLayout.PlacedText placed = LabelLayout.freeText(note);
        graphics.setFont(LabelLayout.font());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(color);
        for (LabelLayout.Line line : placed.lines()) {
            if (!line.text().isEmpty()) {
                graphics.drawString(line.text(), line.baselineX(), line.baselineY());
            }
        }
    }
}
