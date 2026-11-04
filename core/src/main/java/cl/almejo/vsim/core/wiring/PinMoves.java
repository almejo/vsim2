package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.Map;
import java.util.Set;

/**
 * Absolute destinations of pins, planned together against the original circuit. Unlisted pins stay put.
 * {@code addedPinPlaces} are where the change adds pins the circuit does not have yet (a splitter grouped anew puts
 * {@code b4-7} where {@code b0} was): the rerouted wires keep off them, so a wire that follows its pin never leaves a
 * corner on a new pin.
 */
public record PinMoves(Map<PinRef, GridPoint> after, Set<GridPoint> addedPinPlaces) {

    public PinMoves {
        after = Map.copyOf(after);
        addedPinPlaces = Set.copyOf(addedPinPlaces);
    }

    /** Moves that add no pin. */
    public PinMoves(Map<PinRef, GridPoint> after) {
        this(after, Set.of());
    }
}
