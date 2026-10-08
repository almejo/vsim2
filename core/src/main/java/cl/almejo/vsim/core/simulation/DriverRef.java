package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.value.Value;

/** A pin that imposes a value on a net. */
public record DriverRef(String componentId, int pinIndex, Value driven) {
}
