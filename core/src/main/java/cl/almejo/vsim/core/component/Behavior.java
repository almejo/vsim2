package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.value.Value;

import java.util.Map;

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
     * User clicked bit {@code bit} (0 = least significant) of the component while simulating: a multi-bit switch
     * toggles just that bit. Every other component ignores it.
     */
    default void interactBit(BehaviorContext context, int bit) {
    }

    /**
     * Whether {@code SimulationEngine#restart} carries this component's value over to the new run. True for switches
     * and input pins; a push button starts released.
     */
    default boolean keepsValueOnRestart() {
        return true;
    }

    /**
     * Makes an input component (a switch or an input pin) drive {@code value} from now on (M6.1-C truth tables).
     * Returns false, changing nothing, for every other component.
     */
    default boolean setValue(BehaviorContext context, Value value) {
        return false;
    }

    /**
     * The {@linkplain ParameterSpec#liveValue() live value} of the component changed to the one {@code parameters}
     * (resolved) give, while it keeps running: an input drives it now, unless it drives it already (the click that
     * the edit records reached the simulation first). Only called on behaviours of types with a live-value parameter.
     */
    default void takeLiveValue(BehaviorContext context, Map<String, Object> parameters) {
    }

    /**
     * «Restaurar contenido inicial»: a part with contents (a RAM) goes back to the ones it started with, without
     * restarting the rest of the simulation. Every other component ignores it.
     */
    default void restoreInitialState(BehaviorContext context) {
    }
}
