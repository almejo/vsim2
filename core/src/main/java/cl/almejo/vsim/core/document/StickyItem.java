package cl.almejo.vsim.core.document;

import java.util.Objects;

/** One line of a sticky note's checklist. */
public record StickyItem(String text, boolean done) {

    public StickyItem {
        text = Objects.requireNonNullElse(text, "");
    }
}
