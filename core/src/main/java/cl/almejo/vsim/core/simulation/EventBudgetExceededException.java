package cl.almejo.vsim.core.simulation;

/**
 * Thrown by the {@link Scheduler} before it would run an event over the budget set with
 * {@link Scheduler#limitEvents}; {@link SimulationEngine#runWithEventBudget} turns it into an {@link EventBudgetRun}.
 */
final class EventBudgetExceededException extends RuntimeException {

    EventBudgetExceededException(long time) {
        super("event budget exceeded at t = " + time, null, false, false);
    }
}
