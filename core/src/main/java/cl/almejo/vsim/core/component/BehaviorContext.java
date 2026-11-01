package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.value.Value;

import java.util.List;

/** What a behavior can see and do. Supplied by the simulation engine. */
public interface BehaviorContext {
    /** Current value of the net connected to the pin (Z when unconnected or on a width mismatch). */
    Value input(int pinIndex);

    /** Makes the pin impose {@code value} on its net after {@code delay} simulation units (0 allowed). */
    void drive(int pinIndex, Value value, long delay);

    /** Runs {@code action} after {@code delay} units unless the component was removed meanwhile. */
    void schedule(long delay, Runnable action);

    long now();

    /**
     * Asks the running simulation to pause, like a breakpoint (a Terminal received its «Detener al recibir» text): it
     * stops once every event of the current time ran, and the UI says why with the message of bundle key
     * {@code messageKey} and {@code arguments} (core never formats user text). The UI puts the part's name before
     * {@code arguments}, so the message's {@code {0}} is the name and the arguments follow from {@code {1}} (core does
     * not know labels). Stepping and running go on normally
     * afterwards. While one request waits to be served, later ones are dropped. A context outside the engine (a test, a one-off
     * evaluation) ignores it.
     */
    default void requestPause(String messageKey, List<String> arguments) {
    }
}
