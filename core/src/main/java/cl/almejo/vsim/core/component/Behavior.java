package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.value.Value;

/** Simulation logic of one component instance. Called only from the simulation thread. */
public interface Behavior {

    /** Called once when the instance joins the simulation; inputs are already connected. */
    default void initialize(BehaviorContext context) {
    }

    /** Called when the value seen on an input pin changes. */
    void inputChanged(BehaviorContext context, int pinIndex);

    /** Immutable state the UI needs to draw the component (LED on, digits, samples...). */
    default VisibleState visibleState() {
        return VisibleState.NONE;
    }

    default boolean isInteractive() {
        return false;
    }

    /** User clicked the component while simulating (e.g. toggles a switch). */
    default void interact(BehaviorContext context) {
    }

    /**
     * Makes an input component (a switch or an input pin) drive {@code value} from now on (M6.1-C truth tables).
     * Returns false, changing nothing, for every other component.
     */
    default boolean setValue(BehaviorContext context, Value value) {
        return false;
    }

    /**
     * «Restaurar contenido inicial»: a part with contents (a RAM) goes back to the ones it started with, without
     * restarting the rest of the simulation. Every other component ignores it.
     */
    default void restoreInitialState(BehaviorContext context) {
    }
}
