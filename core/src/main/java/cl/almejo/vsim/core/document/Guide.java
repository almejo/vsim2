package cl.almejo.vsim.core.document;

import java.util.Objects;

/**
 * An alignment guide of the circuit: a line across the whole canvas that parts and points snap to. A horizontal
 * guide sits at grid {@code y = position}, a vertical one at grid {@code x = position}. Guides are document data
 * (saved with the circuit, undoable) but never part of the netlist or the simulation.
 */
public record Guide(Orientation orientation, int position) {

    public enum Orientation { HORIZONTAL, VERTICAL }

    public Guide {
        Objects.requireNonNull(orientation, "orientation");
    }
}
