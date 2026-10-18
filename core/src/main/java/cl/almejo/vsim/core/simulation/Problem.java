package cl.almejo.vsim.core.simulation;

/** Something wrong in the circuit that the UI should help the user find. */
public sealed interface Problem permits ShortCircuit, WidthMismatch, DisplayBusOverride {
    String netId();
}
