package cl.almejo.vsim.core.component.appearance;

import java.util.Objects;
import java.util.Optional;

/** A path node, optionally controlling the quadratic segment from it to the following node. */
public record SymbolNode(SymbolPoint point, Optional<SymbolPoint> control) {
    public SymbolNode {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(control, "control");
    }
}
