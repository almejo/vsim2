package cl.almejo.vsim.core.layout;

/**
 * How far «Ordenar circuito» got, for the progress window: the stage and, within it, {@code done} of {@code total}
 * ({@code total} 0 when unknown, as for the rounds of the wire rules). Called on the planning thread.
 */
@FunctionalInterface
public interface CircuitTidyProgress {

    /** The stages, in the order they run. */
    enum Stage { SEPARATING, MOVING, WIRES }

    CircuitTidyProgress NONE = (stage, done, total) -> { };

    void reached(Stage stage, int done, int total);
}
