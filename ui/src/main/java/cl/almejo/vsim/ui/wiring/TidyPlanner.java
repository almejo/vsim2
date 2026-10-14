package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;

import java.util.function.Consumer;

/** Computes the plan some options and scope would apply, in the background, for the dialog's live summary. */
@FunctionalInterface
public interface TidyPlanner {

    /**
     * Starts a computation; {@code onResult} runs on the EDT unless the returned runnable (cancel) was called first.
     *
     * @param selectionOnly true = only the selected wires may change
     */
    Runnable plan(TidyOptions options, boolean selectionOnly, Consumer<TidyPlan> onResult);
}
