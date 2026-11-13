package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.value.Value;

import java.util.Objects;

/**
 * The value of a net on the signal card: «sin simular» when no snapshot of the current netlist is shown (stale or
 * reset: never a wrong value), else the value with its Z and X bits.
 */
public sealed interface NetValue {

    /** No snapshot of the current netlist: the net still highlights, its value reads «sin simular». */
    record NotSimulating() implements NetValue {
    }

    /** The value the latest current snapshot gives the net. */
    record Simulated(Value value) implements NetValue {
        public Simulated {
            Objects.requireNonNull(value, "value");
        }
    }
}
