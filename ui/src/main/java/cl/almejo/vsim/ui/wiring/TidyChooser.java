package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.wiring.TidyOptions;

import java.awt.Component;
import java.util.Optional;

/** Asks the user for the tidy options (the real one shows {@link TidyWiresDialog}); empty = cancelled. */
@FunctionalInterface
public interface TidyChooser {

    /**
     * @param hasSelection whether «Selección» is available
     * @param planner      computes in the background the plan the options and scope would apply, for the summary
     */
    Optional<TidyChoice> choose(Component parent, boolean hasSelection, TidyOptions initial,
                                TidyPlanner planner);
}
