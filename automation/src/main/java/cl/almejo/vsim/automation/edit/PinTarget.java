package cl.almejo.vsim.automation.edit;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A pin an edit names: the part ({@code c12}, or {@code new:<alias>} for a part placed earlier in the same call) and
 * the pin's name, with its index when the name repeats; or only the index. Never pixel coordinates (A§5.1).
 */
public record PinTarget(String target, Optional<String> pinName, OptionalInt index) {

    public PinTarget {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(pinName, "pinName");
        Objects.requireNonNull(index, "index");
        if (pinName.isEmpty() && index.isEmpty()) {
            throw new IllegalArgumentException("a pin is named by its name, its index or both");
        }
    }

    /** The pin {@code pinName} of {@code target}. */
    public static PinTarget named(String target, String pinName) {
        return new PinTarget(target, Optional.of(pinName), OptionalInt.empty());
    }
}
