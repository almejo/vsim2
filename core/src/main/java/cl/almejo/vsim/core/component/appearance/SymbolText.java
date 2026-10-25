package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.document.NoteColor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An annotation of one or more lines (the text holds a {@code \n} between them; every line keeps the alignment); an
 * absent text follows the component definition's name. {@code color} is the letters' colour and {@code border} the
 * box drawn around the text, both from the notes' palette; {@code NONE} is the plain look (the scheme's text colour,
 * no border).
 */
public record SymbolText(SymbolPoint anchor, Optional<String> text, TextSize size, TextAlignment alignment,
                         NoteColor color, NoteColor border) implements SymbolElement {
    public SymbolText {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(border, "border");
        if (text.stream().anyMatch(value -> value.contains("\r"))) {
            throw new IllegalArgumentException("symbol texts separate their lines with \\n only");
        }
    }

    /** A plain text: no letter colour and no border. */
    public SymbolText(SymbolPoint anchor, Optional<String> text, TextSize size, TextAlignment alignment) {
        this(anchor, text, size, alignment, NoteColor.NONE, NoteColor.NONE);
    }

    /** The lines of {@code shown}, in order; an empty text is one empty line. */
    public static List<String> linesOf(String shown) {
        return List.of(shown.split("\n", -1));
    }

    /** The lines of the typed text; one empty line for a text that follows the name. */
    public List<String> lines() {
        return linesOf(text.orElse(""));
    }

    /** This text moved to {@code newAnchor}, with everything else kept. */
    public SymbolText movedTo(SymbolPoint newAnchor) {
        return new SymbolText(newAnchor, text, size, alignment, color, border);
    }

    /** This text with those letter and border colours. */
    public SymbolText coloured(NoteColor newColor, NoteColor newBorder) {
        return new SymbolText(anchor, text, size, alignment, newColor, newBorder);
    }
}
