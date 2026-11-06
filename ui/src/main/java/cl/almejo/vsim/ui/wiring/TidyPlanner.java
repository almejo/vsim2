package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.layout.CircuitTidyOptions;
import cl.almejo.vsim.core.layout.CircuitTidyPlan;

import java.util.function.Consumer;

/** Computes the plan some options and scope would apply, in the background, for the dialog's live summary. */
@FunctionalInterface
public interface TidyPlanner {

    /**
     * Starts a computation; {@code onResult} runs on the EDT unless the returned runnable (cancel) was called first.
     *
     * @param selectionOnly true = only the selected elements may change
     */
    Runnable plan(CircuitTidyOptions options, boolean selectionOnly, Consumer<CircuitTidyPlan> onResult);
}
