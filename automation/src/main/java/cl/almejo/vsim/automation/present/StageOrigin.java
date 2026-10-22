package cl.almejo.vsim.automation.present;

/**
 * Where the AI builds a stage in «Presentar» (spec §6): the free point, in grid units, to the right of what the circuit
 * already holds, where the stage's top-left corner goes.
 */
public record StageOrigin(int x, int y) {
}
