package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.layout.CircuitTidyOptions;
import cl.almejo.vsim.core.layout.CircuitTidyPlan;

/** Computes the plan some options and scope would apply, in the background, for the dialog's live summary. */
@FunctionalInterface
public interface TidyPlanner {

    /** Where a computation ends: its plan, or why there is none. Called on the EDT. */
    interface Results {

        void accept(CircuitTidyPlan plan);

        /** The planning threw; {@code message} says why (never shown as «ya está ordenado»). */
        void failed(String message);

        /**
         * The circuit changed after this computation started, so its plan no longer describes it: compute again. Only
         * the latest computation hears it, once per burst of changes.
         */
        default void stale() {
        }
    }

    /**
     * Starts a computation; {@code results} hears of it on the EDT unless the returned runnable (cancel) was called
     * first.
     *
     * @param selectionOnly true = only the selected elements may change
     */
    Runnable plan(CircuitTidyOptions options, boolean selectionOnly, Results results);
}
