package cl.almejo.vsim.core.simulation;

import java.util.List;

/** Pins of different widths were joined in one net. */
public record WidthMismatch(String netId, List<FlatPinRef> pins) implements Problem {
    public WidthMismatch {
        pins = List.copyOf(pins);
    }
}
