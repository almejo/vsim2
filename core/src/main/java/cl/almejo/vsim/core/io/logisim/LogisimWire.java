package cl.almejo.vsim.core.io.logisim;

import java.util.Objects;

/** A Logisim {@code <wire>}: a straight segment between two points (Logisim joins wires whose ends touch). */
public record LogisimWire(LogisimPoint from, LogisimPoint to) {

    public LogisimWire {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
