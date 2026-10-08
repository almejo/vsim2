package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.Objects;

public record TextLabel(ElementId id, GridPoint position, String text) implements DocumentElement {

    public TextLabel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        text = text == null ? "" : text;
    }

    public TextLabel withText(String newText) {
        return new TextLabel(id, position, newText);
    }

    @Override
    public TextLabel translate(int deltaX, int deltaY) {
        return new TextLabel(id, position.translate(deltaX, deltaY), text);
    }
}
