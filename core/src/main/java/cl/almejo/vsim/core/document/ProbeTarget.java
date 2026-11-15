package cl.almejo.vsim.core.document;

import java.util.Objects;

/**
 * What a probe watches: a component pin (also for wires whose net has a pin), a wire of a net without pins, or the
 * state of a «Máquina de estados» piece.
 */
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

    /**
     * The state of the machine piece {@code componentId} (hito 2 of «Máquinas de estado»): the hidden state net the
     * simulation gives that copy, whose values are state codes the panel writes as state names. Wires never change
     * it; it resolves while the piece exists and is a machine.
     */
    record MachineStateTarget(ElementId componentId) implements ProbeTarget {
        public MachineStateTarget {
            Objects.requireNonNull(componentId, "componentId");
        }
    }

    /** A wire of a net without pins; wire ids change when wires merge or split, so only pin-less nets use it. */
    record WireTarget(ElementId wireId) implements ProbeTarget {
        public WireTarget {
            Objects.requireNonNull(wireId, "wireId");
        }
    }
}
