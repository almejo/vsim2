package cl.almejo.vsim.ui.sheets;

import java.awt.Rectangle;
import java.util.Objects;

/** A «Hoja n» on a pane: its text and the box it is painted in, in logical screen pixels. */
record SheetLabel(String text, Rectangle box) {

    SheetLabel {
        Objects.requireNonNull(text, "text");
        box = new Rectangle(box);
    }

    @Override
    public Rectangle box() {
        return new Rectangle(box);
    }
}
