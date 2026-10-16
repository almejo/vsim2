package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;

import java.util.Set;

/**
 * The light clean-up that follows a move which stretched wires (M6.7): only the wires the move touched are tidied,
 * and only by merging (collinear wires that touch end to end or overlap). Stubs are not removed and detours are not
 * flattened; those stay in the manual tidy. There is no zero-length rule because {@code Wire} rejects zero-length
 * wires, so a document cannot hold one.
 *
 * <p>It reuses {@link WireTidy}, so a merge that would change what is connected (a pin at the shared point, a
 * perpendicular wire crossing there) is skipped. The result is an ordinary {@link TidyPlan}: a command applies it
 * with {@code TidyWiresCommand}, which also reverts it exactly (ids, draw order, probes).
 */
public final class TouchedWiresCleanup {

    /** Merge only: no stub removal, no detour flattening. */
    private static final TidyOptions OPTIONS = new TidyOptions(false, true, false);

    private TouchedWiresCleanup() {
    }

    /**
     * @param document       the document as it is after the move was applied
     * @param touchedWireIds the wires the move created or changed; the only ones that may change
     * @return what to remove and add; empty when there is nothing to do
     */
    public static TidyPlan plan(CircuitDocument document, ComponentTypeRegistry registry,
                                Set<ElementId> touchedWireIds) {
        if (touchedWireIds.isEmpty()) {
            return TidyPlan.EMPTY;
        }
        return WireTidy.plan(document, registry, touchedWireIds, OPTIONS);
    }
}
