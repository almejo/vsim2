package cl.almejo.vsim.core.component;

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
}
