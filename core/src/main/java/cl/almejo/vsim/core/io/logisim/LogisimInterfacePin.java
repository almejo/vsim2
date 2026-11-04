package cl.almejo.vsim.core.io.logisim;

import java.util.List;
import java.util.Objects;

/**
 * A {@code Pin} of a Logisim circuit, which becomes a pin of its custom component: its {@code label} as written
 * (maybe blank), its width, whether it is an output, its {@code location} in the circuit (the key a {@code circ-port}
 * names it by) and its {@code facing} (the default appearance puts it on the opposite side).
 */
public record LogisimInterfacePin(String label, int width, boolean output, LogisimPoint location, String facing) {

    private static final String PIN = "Pin";

    public LogisimInterfacePin {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(facing, "facing");
    }

    /** The {@code Pin}s of {@code circuit}, in file order. */
    public static List<LogisimInterfacePin> of(LogisimCircuit circuit) {
        return circuit.components().stream()
                .filter(component -> component.library().equals(LogisimLibraries.WIRING)
                        && component.name().equals(PIN))
                .map(LogisimInterfacePin::of)
                .toList();
    }

    private static LogisimInterfacePin of(LogisimComponent pin) {
        LogisimAttributes attributes = pin.attributes();
        return new LogisimInterfacePin(attributes.textOr("label", ""), attributes.integer("width"),
                attributes.bool("output"), pin.location(), attributes.textOr("facing", "east"));
    }
}
