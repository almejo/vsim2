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
 * @param centered     whether the field centers its text, like the painted one
 */
public record InlineEditRequest(Rectangle2D documentArea, Font font, String initialText,
                                Function<String, Optional<String>> validator, Consumer<String> onConfirm, boolean centered) {

    public InlineEditRequest(Rectangle2D documentArea, Font font, String initialText,
                             Function<String, Optional<String>> validator, Consumer<String> onConfirm) {
        this(documentArea, font, initialText, validator, onConfirm, false);
    }

    public InlineEditRequest {
        Objects.requireNonNull(documentArea, "documentArea");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(initialText, "initialText");
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(onConfirm, "onConfirm");
    }
}
