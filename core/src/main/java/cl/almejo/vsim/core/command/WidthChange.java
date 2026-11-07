package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.ElementId;

import java.util.Objects;

/**
 * A part that took the width of what it was connected to ({@link ConnectedWidths}): the constant {@code c3} went from
 * 1 to 8 bits when a wire joined it to an 8-bit bus.
 */
public record WidthChange(ElementId componentId, String typeId, int previousWidth, int newWidth) {

    public WidthChange {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(typeId, "typeId");
    }
}
