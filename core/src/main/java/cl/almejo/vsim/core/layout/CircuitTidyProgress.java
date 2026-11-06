package cl.almejo.vsim.core.layout;

/**
 * How far «Ordenar circuito» got, for the progress window: the stage and, within it, {@code done} of {@code total}
 * ({@code total} 0 when unknown, as for the rounds of the wire rules). Called on the planning thread.
 */
@FunctionalInterface
public interface CircuitTidyProgress {

    /**
     * The stages, in the order they run: {@code SEPARATING} with each round of parts (of at most three), {@code MOVING}
     * with each part placed (of the parts of that round), {@code ALIGNING} with each column (of the columns found),
     * {@code STRAIGHTENING} with each round (of at most ten), {@code MOVING_TEXTS} with each block of texts moved off a
     * body (of those blocks) and {@code WIRES} with each pass of the wire rules (total unknown, 0).
     */
    enum Stage { SEPARATING, MOVING, ALIGNING, STRAIGHTENING, MOVING_TEXTS, WIRES }

    CircuitTidyProgress NONE = (stage, done, total) -> { };

    void reached(Stage stage, int done, int total);
}
