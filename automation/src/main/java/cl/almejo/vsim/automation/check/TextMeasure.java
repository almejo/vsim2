package cl.almejo.vsim.automation.check;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Optional;

/**
 * Where texts are drawn, in grid units, for the {@link LayoutCheck}: the editor measures them with its real font (the
 * automation layer has no fonts); {@link #APPROXIMATE} guesses from the number of characters, for hosts without one.
 */
public interface TextMeasure {

    /** The box of {@code component}'s label as the editor draws it; empty for a blank label. */
    Optional<TextBox> componentLabel(ComponentType type, Component component);

    /** The box of a free text (a note). */
    TextBox freeText(TextLabel label);

    /** A text's box in grid units. */
    record TextBox(double left, double top, double width, double height) {

        /** True when the two boxes share some area (touching edges do not). */
        public boolean overlaps(TextBox other) {
            return left < other.left + other.width && other.left < left + width
                    && top < other.top + other.height && other.top < top + height;
        }

        public static TextBox of(Bounds bounds) {
            return new TextBox(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        }
    }

    /**
     * The editor's placement rules (a label above an upright body, left of a sideways one, centred on its place when
     * the user moved it; a note from its top-left corner, as wide as its longest line and at least one unit wide)
     * with a 10 px font guessed at 5.5 px per character and 12 px per line, 8 px per grid unit.
     */
    TextMeasure APPROXIMATE = new TextMeasure() {
        private static final double CHARACTER_WIDTH = 5.5 / GridPoint.PIXELS_PER_UNIT;
        private static final double LINE_HEIGHT = 12.0 / GridPoint.PIXELS_PER_UNIT;
        private static final double LABEL_GAP = 3.0 / GridPoint.PIXELS_PER_UNIT;

        @Override
        public Optional<TextBox> componentLabel(ComponentType type, Component component) {
            if (component.label().isBlank()) {
                return Optional.empty();
            }
            double width = component.label().length() * CHARACTER_WIDTH;
            if (component.labelOffset().isPresent()) {
                LabelOffset offset = component.labelOffset().get();
                GridPoint centre = component.position().plus(
                        component.rotation().apply(new GridPoint(offset.x(), offset.y())));
                return Optional.of(new TextBox(centre.x() - width / 2, centre.y() - LINE_HEIGHT / 2, width,
                        LINE_HEIGHT));
            }
            Bounds body = type.bounds(component);
            boolean sideways = component.rotation() == Rotation.R90 || component.rotation() == Rotation.R270;
            double left = sideways ? body.x() - LABEL_GAP - width : body.x() + body.width() / 2.0 - width / 2;
            double top = sideways ? body.y() + body.height() / 2.0 - LINE_HEIGHT / 2
                    : body.y() - LABEL_GAP - LINE_HEIGHT;
            return Optional.of(new TextBox(left, top, width, LINE_HEIGHT));
        }

        @Override
        public TextBox freeText(TextLabel label) {
            String[] lines = label.text().split("\\R", -1);
            int longest = 0;
            for (String line : lines) {
                longest = Math.max(longest, line.length());
            }
            return new TextBox(label.position().x(), label.position().y(), Math.max(1, longest * CHARACTER_WIDTH),
                    lines.length * LINE_HEIGHT);
        }
    };
}
