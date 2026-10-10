package cl.almejo.vsim.core.document;

import java.util.Objects;

/** What a probe watches: a component pin (also for wires whose net has a pin) or a wire of a net without pins. */
public sealed interface ProbeTarget {

    /** Pin {@code pinIndex} of component {@code componentId}; pins survive edits, so probes anchor here when they can. */
    record PinTarget(ElementId componentId, int pinIndex) implements ProbeTarget {
        public PinTarget {
            Objects.requireNonNull(componentId, "componentId");
            if (pinIndex < 0) {
                throw new IllegalArgumentException("negative pin index " + pinIndex);
            }
        }
    }

    /** A wire of a net without pins; wire ids change when wires merge or split, so only pin-less nets use it. */
    record WireTarget(ElementId wireId) implements ProbeTarget {
        public WireTarget {
            Objects.requireNonNull(wireId, "wireId");
        }
    }
}
