package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.wiring.TidyPlan;

import java.util.Set;

/**
 * What «Ordenar circuito» proposes: {@code after} is a new document (the original is untouched), with the counts for
 * the summary, the items it could not separate, the wire rules' own plan, the parts aligned in columns, the
 * driver–receiver pairs straightened and the wire corners before and after (spec §5.3).
 */
public record CircuitTidyPlan(CircuitDocument after, int partsMoved, int textsMoved, Set<ElementId> notSeparated,
                              TidyPlan wires, int partsAligned, int pairsStraightened, int cornersBefore,
                              int cornersAfter) {

    public CircuitTidyPlan {
        notSeparated = Set.copyOf(notSeparated);
    }

    /** A plan without aligning or straightening (zero counts, corners unknown). */
    public CircuitTidyPlan(CircuitDocument after, int partsMoved, int textsMoved, Set<ElementId> notSeparated,
                           TidyPlan wires) {
        this(after, partsMoved, textsMoved, notSeparated, wires, 0, 0, 0, 0);
    }

    /** True when nothing moved and the wire rules change nothing. */
    public boolean isEmpty() {
        return partsMoved == 0 && textsMoved == 0 && wires.isEmpty();
    }
}
