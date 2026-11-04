package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How one of the file's circuits is a VSim part (the subcircuit step makes it): its type in the registry the layout
 * uses, the parameters an instance takes, and which VSim pin (its index) each of the circuit's Logisim {@code Pin}s
 * becomes, by the pin's location inside the circuit (the name of the instance's {@link LogisimEnd}). {@code facing} is
 * the Logisim direction an unturned instance faces (its drawing's), and {@code anchor}, when known, the point of the
 * unturned part (from its origin, in grid units) that lands on the instance's Logisim location: an instance with no
 * connected pin goes there.
 */
public record SubcircuitPart(String typeId, Map<String, Object> parameters, Map<LogisimPoint, Integer> pinOfInnerPin,
                             String facing, Optional<GridPoint> anchor) {

    public SubcircuitPart {
        Objects.requireNonNull(typeId, "typeId");
        parameters = Map.copyOf(parameters);
        pinOfInnerPin = Map.copyOf(pinOfInnerPin);
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(anchor, "anchor");
    }

    /** A part whose unturned instances face east, with no known anchor. */
    public SubcircuitPart(String typeId, Map<String, Object> parameters, Map<LogisimPoint, Integer> pinOfInnerPin) {
        this(typeId, parameters, pinOfInnerPin, "east", Optional.empty());
    }

    /** How an instance facing {@code instanceFacing} in Logisim turns: from this part's facing to it. */
    public Rotation rotationFor(String instanceFacing) {
        LogisimFacing own = facingOr(facing, LogisimFacing.EAST);
        return facingOr(instanceFacing, own).rotationFrom(own);
    }

    private static LogisimFacing facingOr(String text, LogisimFacing fallback) {
        try {
            return LogisimFacing.parse(text);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
