package cl.almejo.vsim.core.component.appearance;

import java.util.Objects;
import java.util.Optional;

/** A single-line annotation; an absent text follows the component definition's name. */
public record SymbolText(SymbolPoint anchor, Optional<String> text, TextSize size, TextAlignment alignment)
        implements SymbolElement {
    public SymbolText {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(alignment, "alignment");
        if (text.stream().anyMatch(value -> value.contains("\n") || value.contains("\r"))) {
            throw new IllegalArgumentException("symbol texts must occupy one line");
        }
    }
}
