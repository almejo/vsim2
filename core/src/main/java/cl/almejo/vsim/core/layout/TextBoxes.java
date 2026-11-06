package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.Optional;

/**
 * The size of texts on the canvas, in grid units rounded outward. Core has no fonts: the UI measures them as it paints
 * them; tests and the AI may use {@link #NONE}.
 */
public interface TextBoxes {

    /** No text takes any room: components have no label box and a free text is a point at its position. */
    TextBoxes NONE = new TextBoxes() {
        @Override
        public Optional<Bounds> componentLabel(Component component) {
            return Optional.empty();
        }

        @Override
        public Bounds freeText(TextLabel label) {
            return new Bounds(label.position().x(), label.position().y(), 0, 0);
        }
    };

    /** The box of the component's label where it is drawn; empty when it shows none. */
    Optional<Bounds> componentLabel(Component component);

    /** The box of a free text (a note on the canvas). */
    Bounds freeText(TextLabel label);
}
