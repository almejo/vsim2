package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.io.CircuitFragment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A circuit built from a table: the fragment to open or paste (it keeps the ids of the scratch document it was built
 * in; pasting gives fresh ones) and its gate counts, the same {@link CircuitGenerator#cost} gives.
 */
public record GeneratedCircuit(CircuitFragment fragment, int gateCount, Map<String, Integer> gatesByType) {

    public GeneratedCircuit {
        gatesByType = Collections.unmodifiableMap(new LinkedHashMap<>(gatesByType));
    }

    public GateCost cost() {
        return new GateCost(gateCount, gatesByType);
    }
}
