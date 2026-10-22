package cl.almejo.vsim.automation.present;

/**
 * Where the AI builds a stage that begins (spec §6), in grid units: the free place to the right of the previous
 * stage's frame, its parts starting here so that the stage's frame lines up with the previous one's top.
 */
public record StageOrigin(int x, int y) {
}
