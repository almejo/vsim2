package cl.almejo.vsim.core.layout;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Moves overlapping boxes apart by the least it can, keeping each pair's order (spec «Ordenar circuito» §4.3).
 * Pure geometry: it knows nothing about components, wires or documents.
 */
public final class OverlapSeparation {

    private OverlapSeparation() {
    }

    /**
     * @throws java.util.concurrent.CancellationException when {@code cancelled} says yes
     */
    public static Separation separate(List<LayoutBox> boxes, BooleanSupplier cancelled) {
        throw new UnsupportedOperationException("Task 1");
    }
}
