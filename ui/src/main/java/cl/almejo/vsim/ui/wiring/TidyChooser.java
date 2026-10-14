package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;

import java.awt.Component;
import java.util.Optional;
import java.util.function.BiFunction;

/** Asks the user for the tidy options (the real one shows {@link TidyWiresDialog}); empty = cancelled. */
@FunctionalInterface
public interface TidyChooser {

    /**
     * @param hasSelection whether «Selección» is available
     * @param planner      the plan the given options and scope (true = selection only) would apply, for the summary
     */
    Optional<TidyChoice> choose(Component parent, boolean hasSelection, TidyOptions initial,
                                BiFunction<TidyOptions, Boolean, TidyPlan> planner);
}
