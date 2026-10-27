package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import cl.almejo.vsim.ui.paint.TextFonts;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where label text goes, in document coordinates (8 px per grid unit at zoom 1). Painting, hit testing and
 * circuit bounds all use these numbers. Works without a screen.
 */
public final class LabelLayout {

    /** Space between a component's body and its label, in document pixels. */
    public static final double GAP = 3;
    /** Room between the text of a note with a background or a border and the edge of its box, in document pixels. */
    public static final double NOTE_PADDING = 3;

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final FontRenderContext RENDER_CONTEXT = new FontRenderContext(null, true, true);
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    private LabelLayout() {
    }

    /** The font of component labels and free texts: the current one of Preferencias (M6.8), read at every use. */
    public static Font font() {
        return TextFonts.labelFont();
    }

    /**
     * A text whose box starts at {@code left}/{@code top}: one line for a component's label, one per line break for a
     * note. Each line is drawn at its own baseline ({@link #lines}), {@code lineHeight} below the one before; the box
     * is as wide as the longest line.
     */
    public record PlacedText(String text, List<String> textLines, double left, double top, double width,
                             double height, double ascent, double lineHeight) {

        public PlacedText {
            textLines = List.copyOf(textLines);
        }

        public Rectangle2D bounds() {
            return new Rectangle2D.Double(left, top, width, height);
        }

        public float baselineX() {
            return (float) left;
        }

        /** The baseline of the first line. */
        public float baselineY() {
            return (float) (top + ascent);
        }

        /** Every line with where to draw it, top to bottom. */
        public List<Line> lines() {
            List<Line> lines = new ArrayList<>(textLines.size());
            for (int index = 0; index < textLines.size(); index++) {
                lines.add(new Line(textLines.get(index), baselineX(), (float) (top + ascent + index * lineHeight)));
            }
            return lines;
        }

        private PlacedText movedTo(double newLeft, double newTop, double newWidth) {
            return new PlacedText(text, textLines, newLeft, newTop, newWidth, height, ascent, lineHeight);
        }
    }

    /** One line of a {@link PlacedText}, drawn at ({@code baselineX}, {@code baselineY}). */
    public record Line(String text, float baselineX, float baselineY) {
    }

    /** The lines of a note: split at every line break (\n, \r\n or \r), blank lines included. */
    public static List<String> noteLines(String text) {
        return List.of(LINE_BREAK.split(text, -1));
    }

    /**
     * A free text label (a note): its position is the top-left corner; at least one grid unit wide. Each line break
     * starts a new line, left-aligned under the one before.
     */
    public static PlacedText freeText(TextLabel label) {
        PlacedText measured = measureLines(label.text(), noteLines(label.text()));
        return measured.movedTo(label.position().x() * UNIT, label.position().y() * UNIT,
                Math.max(UNIT, measured.width()));
    }

    /** A component's label: above the body for R0/R180, left of it for R90/R270; empty for a blank label and for a memory (its name is drawn in its own title bar). */
    public static Optional<PlacedText> componentLabel(ComponentType type, Component component) {
        if (component.label().isBlank() || MemoryTypes.kindOf(type.id()).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(componentLabelPlace(type, component, component.label()));
    }

    /**
     * Where {@code text} would be drawn as the label of {@code component}: what {@link #componentLabel} gives for a
     * label with that text, also for an empty one (a zero-width box where the label would start). A label the user
     * moved (M6.8, {@link Component#labelOffset}) is centred on its place and stays horizontal.
     */
    public static PlacedText componentLabelPlace(ComponentType type, Component component, String text) {
        if (component.labelOffset().isPresent()) {
            return movedLabelPlace(component, component.labelOffset().get(), text);
        }
        Rectangle2D body = ComponentDrawing.documentBounds(type, component);
        PlacedText measured = measure(text);
        boolean sideways = component.rotation() == Rotation.R90 || component.rotation() == Rotation.R270;
        double left = sideways ? body.getMinX() - GAP - measured.width()
                : body.getCenterX() - measured.width() / 2;
        double top = sideways ? body.getCenterY() - measured.height() / 2
                : body.getMinY() - GAP - measured.height();
        return measured.movedTo(left, top, measured.width());
    }

    /** The centre of a label moved to {@code offset}: the position plus the offset turned with the component. */
    public static GridPoint movedLabelCentre(Component component, LabelOffset offset) {
        return component.position().plus(component.rotation().apply(new GridPoint(offset.x(), offset.y())));
    }

    /**
     * The label offset of {@code component} whose drawn centre is nearest to ({@code centreX}, {@code centreY}), in
     * document pixels: the centre snapped to a grid point, relative to the position, turned back into the
     * component's own unrotated frame (the inverse of {@link Rotation#apply}).
     */
    public static LabelOffset offsetFor(Component component, double centreX, double centreY) {
        GridPoint relative = new GridPoint((int) Math.round(centreX / UNIT) - component.position().x(),
                (int) Math.round(centreY / UNIT) - component.position().y());
        GridPoint local = Rotation.fromQuarterTurns(-component.rotation().quarterTurns()).apply(relative);
        return new LabelOffset(local.x(), local.y());
    }

    private static PlacedText movedLabelPlace(Component component, LabelOffset offset, String text) {
        GridPoint centre = movedLabelCentre(component, offset);
        PlacedText measured = measure(text);
        return measured.movedTo(centre.x() * UNIT - measured.width() / 2, centre.y() * UNIT - measured.height() / 2,
                measured.width());
    }

    /**
     * The box a note takes, in document pixels: its text box ({@link #freeText}), grown by {@link #NOTE_PADDING} on
     * every side when the note has a background or a border, so the box is drawn around the text and the text stays
     * where it was. Painting, hit testing, the selection outline and the circuit bounds all use it.
     */
    public static Rectangle2D noteBox(TextLabel label) {
        Rectangle2D text = freeText(label).bounds();
        if (!label.style().boxed()) {
            return text;
        }
        return new Rectangle2D.Double(text.getX() - NOTE_PADDING, text.getY() - NOTE_PADDING,
                text.getWidth() + 2 * NOTE_PADDING, text.getHeight() + 2 * NOTE_PADDING);
    }

    /** Bounds of a free text label in grid units: its {@link #noteBox}. */
    public static Rectangle2D gridBounds(TextLabel label) {
        Rectangle2D bounds = noteBox(label);
        return new Rectangle2D.Double(bounds.getX() / UNIT, bounds.getY() / UNIT,
                bounds.getWidth() / UNIT, bounds.getHeight() / UNIT);
    }

    /** A part's label: always one line, whatever it holds (part labels are names, not notes). */
    private static PlacedText measure(String text) {
        return measureLines(text, List.of(text));
    }

    /**
     * {@code lines} of {@code text} at the origin: the first line's ascent plus one line height (ascent, descent and
     * leading) per further line, and the descent of the last; as wide as the longest line. A single line is as tall
     * as its ascent and descent, as ever.
     */
    private static PlacedText measureLines(String text, List<String> lines) {
        Font font = font();
        double width = 0;
        for (String line : lines) {
            if (!line.isEmpty()) {
                width = Math.max(width, font.getStringBounds(line, RENDER_CONTEXT).getWidth());
            }
        }
        String sample = lines.getFirst().isEmpty() ? " " : lines.getFirst();
        LineMetrics metrics = font.getLineMetrics(sample, RENDER_CONTEXT);
        double singleHeight = metrics.getAscent() + metrics.getDescent();
        double lineHeight = singleHeight + metrics.getLeading();
        return new PlacedText(text, lines, 0, 0, width, singleHeight + (lines.size() - 1) * lineHeight,
                metrics.getAscent(), lineHeight);
    }
}
