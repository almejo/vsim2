package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A Logisim component as a VSim part: {@code typeId} and {@code parameters} as VSim's registry has them (already
 * resolved by it, so every value is valid and normalized), the rotation that turns VSim's part the way Logisim's
 * faced, the label, the notes, and the {@link Extras} the later steps of the import need.
 */
public record MappedPart(String typeId, Map<String, Object> parameters, Rotation rotation, String label,
                         List<ImportNote> notes, Extras extras) implements LogisimMapping {

    public MappedPart {
        Objects.requireNonNull(typeId, "typeId");
        parameters = Map.copyOf(parameters);
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(label, "label");
        notes = List.copyOf(notes);
        Objects.requireNonNull(extras, "extras");
    }

    /**
     * What the nets, geometry and subcircuit steps need beyond the part itself.
     *
     * @param notInFront   the gate inputs (from 0) Logisim negates with a bubble that VSim draws as a NOT in front, the
     *                     geometry step places it (only when some, not all, inputs of an AND/OR are negated)
     * @param needsName    a pin without label: the subcircuit step gives it a unique name
     * @param tunnelLabel  a tunnel's Logisim label, the name the nets step groups tunnels by
     * @param splitterEnds a splitter's Logisim end (from 0) → the VSim pin of its group, counted after the bus pin
     *                     (an end without bits has no pin)
     * @param upright      a part whose Logisim drawing never turns (a constant, a clock): the geometry step keeps it
     *                     at its rotation, so its text reads across, and joins its pin with added wire instead
     */
    public record Extras(List<Integer> notInFront, boolean needsName, Optional<String> tunnelLabel,
                         Map<Integer, Integer> splitterEnds, boolean upright) {

        /** Nothing extra. */
        public static final Extras NONE = new Extras(List.of(), false, Optional.empty(), Map.of(), false);

        public Extras {
            notInFront = List.copyOf(notInFront);
            Objects.requireNonNull(tunnelLabel, "tunnelLabel");
            splitterEnds = Collections.unmodifiableSortedMap(new TreeMap<>(splitterEnds));
        }

        public Extras withNotInFront(List<Integer> inputs) {
            return new Extras(inputs, needsName, tunnelLabel, splitterEnds, upright);
        }

        public Extras withNeedsName(boolean needed) {
            return new Extras(notInFront, needed, tunnelLabel, splitterEnds, upright);
        }

        public Extras withTunnelLabel(String label) {
            return new Extras(notInFront, needsName, Optional.of(label), splitterEnds, upright);
        }

        public Extras withSplitterEnds(Map<Integer, Integer> ends) {
            return new Extras(notInFront, needsName, tunnelLabel, ends, upright);
        }

        public Extras withUpright() {
            return new Extras(notInFront, needsName, tunnelLabel, splitterEnds, true);
        }
    }
}
