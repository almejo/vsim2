package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A stand-in for {@code CircuitGenerator.cost} (Task 4, written in parallel): counts the gates of the two-level
 * circuit straight from the terms. One NOT per input that appears complemented, one first-level gate per term with two
 * or more literals (per distinct term when sharing), one second-level gate per output with two or more terms; wide
 * gates split into trees of at most four inputs. «Solo NAND» / «Solo NOR» just rename every gate (an inverter is a
 * two-input gate with its inputs tied), which is close but not exactly what the generator builds.
 * <p>
 * To be replaced by the real cost after merging Task 4: see {@link KarnaughExtension#KarnaughExtension()}.
 */
final class PlaceholderCost implements KarnaughCost {

    static final PlaceholderCost INSTANCE = new PlaceholderCost();

    private static final int MAXIMUM_FAN_IN = 4;

    private PlaceholderCost() {
    }

    @Override
    public Map<String, Integer> gatesByType(Minimization minimization, WizardOptions options) {
        boolean sumOfProducts = minimization.form() == Minimization.Form.SUM_OF_PRODUCTS;
        int inputCount = minimization.spec().inputCount();
        String first = sumOfProducts ? "and" : "or";
        String second = sumOfProducts ? "or" : "and";
        List<String> gates = new ArrayList<>();
        Set<Integer> complemented = new LinkedHashSet<>();
        Set<Term> builtTerms = new LinkedHashSet<>();
        for (Minimization.OutputExpression output : minimization.outputs()) {
            List<Term> terms = output.terms();
            if (terms.isEmpty() || terms.getFirst().mask() == 0) {
                continue; // a constant output: no gates
            }
            for (Term term : terms) {
                for (int input = 0; input < inputCount; input++) {
                    // In a product a variable that stays 0 is complemented; in the sum of a group of 0s, one that stays 1.
                    if (term.uses(input, inputCount) && term.valueOf(input, inputCount) != sumOfProducts) {
                        complemented.add(input);
                    }
                }
                if (term.literalCount() >= 2 && (!options.shareTerms() || builtTerms.add(term))) {
                    gates.addAll(tree(first, term.literalCount()));
                }
            }
            gates.addAll(tree(second, terms.size()));
        }
        for (int count = 0; count < complemented.size(); count++) {
            gates.add("not");
        }
        Map<String, Integer> counts = new TreeMap<>();
        for (String gate : gates) {
            counts.merge(renamed(gate, options.gates()), 1, Integer::sum);
        }
        return counts;
    }

    /** The gates that join {@code inputs} signals with {@code family} gates of at most four inputs. */
    static List<String> tree(String family, int inputs) {
        List<String> gates = new ArrayList<>();
        int remaining = inputs;
        while (remaining > MAXIMUM_FAN_IN) {
            gates.add(family + MAXIMUM_FAN_IN);
            remaining -= MAXIMUM_FAN_IN - 1;
        }
        if (remaining >= 2) {
            gates.add(family + remaining);
        }
        return gates;
    }

    private static String renamed(String gate, WizardOptions.GateChoice choice) {
        if (choice == WizardOptions.GateChoice.AND_OR_NOT) {
            return gate;
        }
        String family = choice == WizardOptions.GateChoice.NAND_ONLY ? "nand" : "nor";
        if (gate.equals("not")) {
            return family + 2;
        }
        return family + gate.replaceAll("[^0-9]", "");
    }
}
