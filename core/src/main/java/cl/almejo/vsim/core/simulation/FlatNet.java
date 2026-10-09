package cl.almejo.vsim.core.simulation;

import java.util.List;

/** A net to simulate. {@code aliases} are ids of nets merged into this one through sub-circuit pins. */
public record FlatNet(String id, List<FlatPinRef> pins, int width, boolean widthMismatch, List<String> aliases) {
    public FlatNet {
        pins = List.copyOf(pins);
        aliases = List.copyOf(aliases);
    }

    public FlatNet(String id, List<FlatPinRef> pins, int width, boolean widthMismatch) {
        this(id, pins, width, widthMismatch, List.of());
    }
}
