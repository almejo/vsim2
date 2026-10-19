package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.CircuitGenerator;
import cl.almejo.vsim.core.logic.Minimization;

import java.util.Map;

/**
 * What the circuit of a minimization would cost: gates by component type id ({@code "and2" → 3}), as the generator
 * would build them. Step 4 turns it into the cost line with {@link CostLine}.
 */
@FunctionalInterface
public interface KarnaughCost {

    /** The generator's own count ({@link CircuitGenerator#cost}): the cost line always matches the built circuit. */
    KarnaughCost GENERATOR = (minimization, options) ->
            CircuitGenerator.cost(minimization, options.generatorOptions()).gatesByType();

    Map<String, Integer> gatesByType(Minimization minimization, WizardOptions options);
}
