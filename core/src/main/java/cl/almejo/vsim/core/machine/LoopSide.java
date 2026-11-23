package cl.almejo.vsim.core.machine;

/**
 * The side of its state a self loop is drawn on. A loop without one («no side saved») keeps the placement every loop
 * had before the user could choose: the first one on top and the next ones turning around the state.
 */
public enum LoopSide {
    TOP, RIGHT, BOTTOM, LEFT
}
