package cl.almejo.vsim.ui.inline;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What the {@link InlineTextEditor} edits (M6.5).
 *
 * @param documentArea where the text is drawn, in document pixels at 100 % (the coordinates painters use)
 * @param font         the font the text is painted with, at 100 %
 * @param initialText  the text the field starts with, all selected
 * @param validator    the problem with a text, empty when it is valid; asked on every keystroke
 * @param onConfirm    what to do with the confirmed text
 * @param anchor       which edge of the area the field keeps as its text grows, and how it aligns the text
 */
public record InlineEditRequest(Rectangle2D documentArea, Font font, String initialText,
                                Function<String, Optional<String>> validator, Consumer<String> onConfirm,
                                Anchor anchor) {

    /** Where the field stays put while it grows with the text, like the painted text it covers. */
    public enum Anchor {
        /** The left edge stays; the field grows to the right (left-aligned text). */
        LEFT,
        /** The field stays centered on the area and grows both ways (centered text). */
        CENTER,
        /**
         * The right edge stays; the field grows to the left (right-aligned text), for a text drawn left of what it
         * labels (M6.8: a sideways component label grows away from the body).
         */
        RIGHT
    }

    /** A left-anchored request. */
    public InlineEditRequest(Rectangle2D documentArea, Font font, String initialText,
                             Function<String, Optional<String>> validator, Consumer<String> onConfirm) {
        this(documentArea, font, initialText, validator, onConfirm, Anchor.LEFT);
    }

    public InlineEditRequest {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(documentArea, "documentArea");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(initialText, "initialText");
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(onConfirm, "onConfirm");
    }
}
