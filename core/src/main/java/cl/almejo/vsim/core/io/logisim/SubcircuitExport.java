package cl.almejo.vsim.core.io.logisim;

import java.util.Map;
import java.util.Objects;

/**
 * A custom component as it was exported: the Logisim circuit it became and, for each pin of its box (by index), where
 * the Logisim {@code Pin} inside that circuit is, which names the instance's end for that pin.
 */
record SubcircuitExport(String circuit, Map<Integer, LogisimPoint> innerPins) {

    SubcircuitExport {
        Objects.requireNonNull(circuit, "circuit");
        innerPins = Map.copyOf(innerPins);
    }
}
