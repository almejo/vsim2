package cl.almejo.vsim.core.simulation;

/**
 * A {@link StepResult} published by {@link SimulationThread#latestStep()}; {@code sequence} grows by one with every
 * published step, so a reader can tell a new step from one it already showed.
 */
public record StepReport(long sequence, StepResult result) {
}
