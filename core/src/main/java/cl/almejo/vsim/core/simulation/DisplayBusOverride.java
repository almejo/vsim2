package cl.almejo.vsim.core.simulation;

/**
 * A soft warning: 7-segment display {@code componentId} has a driver on its bus input and on at least one of its
 * one-bit inputs, so the bus wins and the bit wires are ignored. {@code netId} is the net of the bus input. It is
 * about wiring (pins joined to a driver pin), not about values: a bus driven only by Z still raises it.
 */
public record DisplayBusOverride(String netId, String componentId) implements Problem {
}
