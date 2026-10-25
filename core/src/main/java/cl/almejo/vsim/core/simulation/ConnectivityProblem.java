package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.List;
import java.util.Objects;

/**
 * A structural fault with its instance path and local position: a pin or a loose wire end inside a component's
 * body, an outer terminal whose interface element is unconnected inside, or (listed by the editor, not found by the
 * checks) a pin loosened by a shape change or a piece whose library update would break it.
 */
public record ConnectivityProblem(String netId, String elementId, int pinIndex, GridPoint position, Cause cause)
        implements Problem {

    /** What kind of fault this is; it decides how the editor marks and words it. */
    public enum Cause {
        /** A pin of a part inside a component's body connects to nothing (at any depth). */
        INNER_PIN,
        /** A wire end inside a component's body touches nothing. */
        LOOSE_WIRE_END,
        /** Outer terminal {@code pinIndex} of piece {@code elementId}: its interface element is unconnected inside. */
        UNCONNECTED_INTERFACE,
        /** A pin of a placed piece left loose by «Aplicar y conectar a mano» (session only). */
        LOOSENED_BY_SHAPE_CHANGE,
        /**
         * The piece {@code elementId} uses an older version of a definition than the library's and updating it would
         * break inner connections; {@code netId} is {@code "version:"} plus that definition's id.
         */
        OUTDATED_WOULD_BREAK,
        /**
         * Like {@link #OUTDATED_WOULD_BREAK}, but the definition is used inside another component's body and its pins
         * or size changed, so «Actualizar» is refused and the piece keeps its old copy; the containing names are
         * found with {@code DefinitionUpdateSafety.containingNames}.
         */
        OUTDATED_CANNOT_UPDATE
    }

    public ConnectivityProblem {
        Objects.requireNonNull(netId, "netId");
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(cause, "cause");
        if (pinIndex < -1) {
            throw new IllegalArgumentException("pin index must be nonnegative or -1 for a loose wire end");
        }
    }

    /** A pin of a part inside a body ({@code pinIndex >= 0}) or a loose wire end inside a body ({@code -1}). */
    public ConnectivityProblem(String netId, String elementId, int pinIndex, GridPoint position) {
        this(netId, elementId, pinIndex, position, pinIndex >= 0 ? Cause.INNER_PIN : Cause.LOOSE_WIRE_END);
    }

    /** Whether this warns about a pin; a loose wire end has the documented sentinel index -1. */
    public boolean isPin() {
        return pinIndex >= 0;
    }

    /**
     * The element ids from the piece on the canvas down to the faulty element: {@code [A2, A1, AND]}, so that
     * «Editar por dentro» can travel to the inner part later; the first one is what the canvas shows and selects.
     */
    public List<ElementId> path() {
        return InstancePath.segments(elementId).stream().map(ElementId::new).toList();
    }
}
