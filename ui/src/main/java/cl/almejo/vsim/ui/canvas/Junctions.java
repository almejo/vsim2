package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Points where wires join in a way the eye cannot tell from a crossing; the canvas marks them with a dot. */
public final class Junctions {

    private Junctions() {
    }

    public static Set<GridPoint> of(CircuitDocument document) {
        List<Wire> wires = document.wires();
        Set<GridPoint> junctions = new HashSet<>();
        for (Wire wire : wires) {
            for (GridPoint endpoint : List.of(wire.start(), wire.end())) {
                int branches = 0;
                for (Wire other : wires) {
                    if (other.start().equals(endpoint) || other.end().equals(endpoint)) {
                        branches += 1;
                    } else if (other.contains(endpoint)) {
                        branches += 2;
                    }
                }
                if (branches >= 3) {
                    junctions.add(endpoint);
                }
            }
        }
        return junctions;
    }
}
