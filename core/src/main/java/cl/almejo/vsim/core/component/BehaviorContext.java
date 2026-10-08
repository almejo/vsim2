package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.value.Value;

/** What a behavior can see and do. Supplied by the simulation engine. */
public interface BehaviorContext {
    /** Current value of the net connected to the pin (Z when unconnected or on a width mismatch). */
    Value input(int pinIndex);

    /** Makes the pin impose {@code value} on its net after {@code delay} simulation units (0 allowed). */
    void drive(int pinIndex, Value value, long delay);

    /** Runs {@code action} after {@code delay} units unless the component was removed meanwhile. */
    void schedule(long delay, Runnable action);

    long now();
}
