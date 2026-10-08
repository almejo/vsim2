package cl.almejo.vsim.core.simulation;

import java.util.List;

public record FlatNet(String id, List<FlatPinRef> pins, int width, boolean widthMismatch) {
    public FlatNet {
        pins = List.copyOf(pins);
    }
}
