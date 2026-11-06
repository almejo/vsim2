package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.List;
import java.util.Objects;

/**
 * A structural fault with its instance path and local position: a pin or a loose wire end inside a component's
 * body, an outer terminal whose interface element is unconnected inside, or (listed by the editor, not found by the
 * checks) a pin loosened by a shape change, a piece whose library update would break it or a piece's own memory
 * contents that do not apply.
 */
public record ConnectivityProblem(String netId, String elementId, int pinIndex, GridPoint position, Cause cause)
        implements Problem {

    /** The start of the net id of an {@link Cause#OWN_CONTENTS_FOR_NOTHING} problem; the memory's path follows. */
    public static final String OWN_CONTENTS_PREFIX = "ownContents:";

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
         * Pin {@code pinIndex} of piece {@code elementId}, added by an edit inside its component, went to another spot
         * of its edge because its own spot touched a wire or a pin there (spec §6.2 a; while that edit is applied).
         */
        PIN_PLACED_ELSEWHERE,
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
        OUTDATED_CANNOT_UPDATE,
        /**
         * The piece {@code elementId} of the circuit keeps its own contents for a memory that is no longer in its
         * component (they are ignored); {@code netId} is {@code "ownContents:"} plus the memory's path inside the
         * piece. Listed by the editor, which offers to forget them.
         */
        OWN_CONTENTS_FOR_NOTHING,
        /**
         * The memory {@code elementId} ({@code c5/c10}: its path through the circuit's piece that keeps the contents)
         * is linked to a file, but that piece starts from its own contents, not from the file (the user, 2026-11-06).
         * Listed by the editor, which offers «Volver al del componente».
         */
        OWN_CONTENTS_OVER_LINK
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
