package cl.almejo.vsim.ui.inline;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.List;
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
 * @param suggestions  texts offered in a list under the field (a tunnel salida offers the entrada names); usually none
 * @param multiLine    true for a text of several lines (a note, IA.3): Shift+Enter adds a line break, Enter still
 *                     confirms; left-anchored and without suggestions
 * @param wraps        true for a multi-line text that wraps at the area's width (a sticky note's paper): the field
 *                     keeps that width instead of growing with the longest line
 */
public record InlineEditRequest(Rectangle2D documentArea, Font font, String initialText,
                                Function<String, Optional<String>> validator, Consumer<String> onConfirm,
                                Anchor anchor, List<String> suggestions, boolean multiLine, boolean wraps) {

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

    /** A one-line request. */
    public InlineEditRequest(Rectangle2D documentArea, Font font, String initialText,
                             Function<String, Optional<String>> validator, Consumer<String> onConfirm,
                             Anchor anchor, List<String> suggestions) {
        this(documentArea, font, initialText, validator, onConfirm, anchor, suggestions, false);
    }

    /** A request without wrapping (every request but a sticky note's). */
    public InlineEditRequest(Rectangle2D documentArea, Font font, String initialText,
                             Function<String, Optional<String>> validator, Consumer<String> onConfirm,
                             Anchor anchor, List<String> suggestions, boolean multiLine) {
        this(documentArea, font, initialText, validator, onConfirm, anchor, suggestions, multiLine, false);
    }

    /**
     * A multi-line request that wraps at the width of {@code documentArea} (a sticky note: the text is edited as wide
     * as the paper it is painted on).
     */
    public static InlineEditRequest wrapped(Rectangle2D documentArea, Font font, String initialText,
                                            Function<String, Optional<String>> validator,
                                            Consumer<String> onConfirm) {
        return new InlineEditRequest(documentArea, font, initialText, validator, onConfirm, Anchor.LEFT, List.of(),
                true, true);
    }

    /** A left-anchored request for a text of several lines (a note): Shift+Enter adds a line, Enter confirms. */
    public static InlineEditRequest multiLine(Rectangle2D documentArea, Font font, String initialText,
                                              Function<String, Optional<String>> validator,
                                              Consumer<String> onConfirm) {
        return new InlineEditRequest(documentArea, font, initialText, validator, onConfirm, Anchor.LEFT, List.of(),
                true);
    }

    /** A left-anchored request. */
    public InlineEditRequest(Rectangle2D documentArea, Font font, String initialText,
                             Function<String, Optional<String>> validator, Consumer<String> onConfirm) {
        this(documentArea, font, initialText, validator, onConfirm, Anchor.LEFT);
    }

    /** A request without suggestions. */
    public InlineEditRequest(Rectangle2D documentArea, Font font, String initialText,
                             Function<String, Optional<String>> validator, Consumer<String> onConfirm,
                             Anchor anchor) {
        this(documentArea, font, initialText, validator, onConfirm, anchor, List.of());
    }

    public InlineEditRequest {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(documentArea, "documentArea");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(initialText, "initialText");
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(onConfirm, "onConfirm");
        suggestions = List.copyOf(suggestions);
    }
}
