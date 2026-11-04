package cl.almejo.vsim.core.io.logisim;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Logisim {@code <circuit>}: its components and wires in file order, its own appearance (maybe empty) and its own
 * attributes ({@code clabel}, {@code clabelup}, {@code clabelfont}: the label drawn in the middle of every instance),
 * with Logisim 2.7's defaults where the file says nothing.
 */
public record LogisimCircuit(String name, List<LogisimComponent> components, List<LogisimWire> wires,
                             LogisimAppearance appearance, LogisimAttributes attributes) {

    /** Logisim 2.7's circuit attributes when the file leaves them out ({@code CircuitAttributes}). */
    public static final Map<String, String> DEFAULT_ATTRIBUTES = Map.of(
            "clabel", "", "clabelup", "east", "clabelfont", "SansSerif plain 12");

    public LogisimCircuit {
        Objects.requireNonNull(name, "name");
        components = List.copyOf(components);
        wires = List.copyOf(wires);
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(attributes, "attributes");
    }

    /** A circuit with the default attributes (no label). */
    public LogisimCircuit(String name, List<LogisimComponent> components, List<LogisimWire> wires,
                          LogisimAppearance appearance) {
        this(name, components, wires, appearance, LogisimAttributes.of(DEFAULT_ATTRIBUTES));
    }
}
