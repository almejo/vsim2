package cl.almejo.vsim.core.wiring;

/**
 * One tidy rule. Rules change the wires only through {@link TidyState#tryChange}, which refuses every change that
 * is out of scope or would alter the connectivity.
 */
interface TidyRule {

    /** Applies the rule everywhere it can; true when at least one change was accepted. */
    boolean apply(TidyState state);
}
