package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.InstancePath;

import java.util.HashSet;
import java.util.Set;

/**
 * The components outlined because they have an event at the next event time (M6.1-B, spec §4.4): the document
 * components of the engine's pending flat ids — a gate nested in a user component outlines its top-level instance —
 * for the netlist of the snapshot they came with ({@link #matches}). Pure.
 */
public record PendingOutline(Set<ElementId> components, long netlistToken) {

    /** Nothing outlined. */
    public static final PendingOutline NONE = new PendingOutline(Set.of(), -1);

    public PendingOutline {
        components = Set.copyOf(components);
    }

    /** The outline of {@code pendingComponents} (flat ids) on {@code document}; ids not in it are skipped. */
    public static PendingOutline of(Set<String> pendingComponents, CircuitDocument document, long netlistToken) {
        Set<ElementId> components = new HashSet<>();
        for (String flatId : pendingComponents) {
            ElementId topLevel = new ElementId(InstancePath.topLevel(flatId));
            if (document.component(topLevel).isPresent()) {
                components.add(topLevel);
            }
        }
        return new PendingOutline(components, netlistToken);
    }

    /** False once the document was recompiled: the ids may mean something else now. */
    public boolean matches(long currentNetlistToken) {
        return netlistToken == currentNetlistToken;
    }
}
