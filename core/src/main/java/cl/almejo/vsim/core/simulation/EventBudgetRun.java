package cl.almejo.vsim.core.simulation;

/**
 * What one {@link SimulationEngine#runWithEventBudget} did: {@code eventsProcessed} events ran, and {@code exceeded}
 * is true when the work stopped because the next event would have gone over the budget (the engine is then left at
 * the time it reached, with that event still pending).
 */
public record EventBudgetRun(long eventsProcessed, boolean exceeded) {
}
