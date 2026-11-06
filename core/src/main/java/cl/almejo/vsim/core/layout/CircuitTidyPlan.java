package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.wiring.TidyPlan;

import java.util.Set;

/**
 * What «Ordenar circuito» proposes: {@code after} is a new document (the original is untouched), with the counts for
 * the summary, the items it could not separate and the wire rules' own plan.
 */
public record CircuitTidyPlan(CircuitDocument after, int partsMoved, int textsMoved, Set<ElementId> notSeparated,
                              TidyPlan wires) {

    public CircuitTidyPlan {
        notSeparated = Set.copyOf(notSeparated);
    }

    /** True when nothing moved and the wire rules change nothing. */
    public boolean isEmpty() {
        return partsMoved == 0 && textsMoved == 0 && wires.isEmpty();
    }
}
