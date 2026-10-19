package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.GroupExplanation;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TermText;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The words of step 3 (spec §3): what each variable does inside a group, the group's size, the algebra behind
 * «¿Por qué?», the constant outputs and the rules. Built from {@link GroupExplanation} data with the bundle texts.
 */
final class KarnaughTexts {

    private static final String SEPARATOR = " · ";
    private static final String INDENT = "    ";

    private KarnaughTexts() {
    }

    /** «A cambia → se va · B = 0 → B' · C = 1 → C · D cambia → se va». */
    static String variablesLine(GroupExplanation explanation) {
        List<String> parts = new ArrayList<>();
        for (GroupExplanation.VariableRole role : explanation.variables()) {
            parts.add(switch (role.behavior()) {
                case CHANGES -> Messages.format("karnaugh.groups.changes", role.name());
                case STAYS_ZERO -> Messages.format("karnaugh.groups.stays", role.name(), "0", role.literal());
                case STAYS_ONE -> Messages.format("karnaugh.groups.stays", role.name(), "1", role.literal());
            });
        }
        return String.join(SEPARATOR, parts);
    }

    /** «4 celdas: quedan 2 variables». */
    static String sizeLine(GroupExplanation explanation) {
        int remaining = explanation.remainingVariables();
        String key = remaining == explanation.variables().size() && remaining > 1
                ? "karnaugh.groups.sizeAll" : "karnaugh.groups.size";
        return Messages.format(key, explanation.cellCount(), remaining);
    }

    /**
     * The lines of «¿Por qué?»: the rule, the cells as full terms, which of them are X (taken as 1, or as 0 in a
     * product of sums), then for each variable that changes the pairs it combines, and the term that is left.
     */
    static List<String> algebra(GroupExplanation explanation, List<String> inputNames) {
        boolean sumOfProducts = explanation.form() == Minimization.Form.SUM_OF_PRODUCTS;
        List<String> lines = new ArrayList<>();
        if (explanation.steps().isEmpty()) {
            lines.add(Messages.text("karnaugh.why.single"));
        } else {
            lines.add(Messages.text(sumOfProducts ? "karnaugh.why.intro.sop" : "karnaugh.why.intro.pos"));
            List<String> cells = new ArrayList<>();
            for (Term cell : explanation.cellTerms()) {
                cells.add(text(cell, inputNames, sumOfProducts));
            }
            lines.add(Messages.format("karnaugh.why.cells", String.join(sumOfProducts ? " + " : "·", cells)));
            dontCareLine(explanation, sumOfProducts).ifPresent(lines::add);
            for (GroupExplanation.AlgebraStep step : explanation.steps()) {
                String name = inputNames.get(step.eliminatedInput());
                lines.add(Messages.format("karnaugh.why.step", name));
                for (GroupExplanation.Combination combination : step.combinations()) {
                    lines.add(INDENT + combination(combination, name, inputNames, sumOfProducts));
                }
            }
        }
        lines.add(Messages.format("karnaugh.why.result", explanation.termText()));
        return lines;
    }

    /**
     * «Las celdas 13 y 15 son X («no importa»): aquí las tomamos como 1 para agrandar el grupo.» (as 0 in a product of
     * sums); empty when the group has no X cell.
     */
    private static Optional<String> dontCareLine(GroupExplanation explanation, boolean sumOfProducts) {
        List<Integer> rows = explanation.dontCareRows();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        String takenAs = sumOfProducts ? "1" : "0";
        if (rows.size() == 1) {
            return Optional.of(Messages.format("karnaugh.why.dontCare.one", String.valueOf(rows.getFirst()),
                    takenAs));
        }
        List<String> numbers = rows.stream().map(String::valueOf).toList();
        String listed = Messages.format("karnaugh.why.list",
                String.join(", ", numbers.subList(0, numbers.size() - 1)), numbers.getLast());
        return Optional.of(Messages.format("karnaugh.why.dontCare.many", listed, takenAs));
    }

    private static String combination(GroupExplanation.Combination combination, String name,
                                      List<String> inputNames, boolean sumOfProducts) {
        String first = text(combination.first(), inputNames, sumOfProducts);
        String second = text(combination.second(), inputNames, sumOfProducts);
        if (combination.result().mask() == 0) {
            return Messages.format(sumOfProducts ? "karnaugh.why.combine.sopLast" : "karnaugh.why.combine.posLast",
                    first, second);
        }
        String result = text(combination.result(), inputNames, sumOfProducts);
        return Messages.format(sumOfProducts ? "karnaugh.why.combine.sop" : "karnaugh.why.combine.pos",
                first, second, result, name);
    }

    private static String text(Term term, List<String> inputNames, boolean sumOfProducts) {
        return sumOfProducts ? TermText.product(term, inputNames) : TermText.sum(term, inputNames);
    }

    /**
     * What step 3 says about a constant output instead of (or above) its groups: «F = 0: no hay unos que agrupar.»,
     * «F = 1: todo el mapa es un solo grupo.» and their product-of-sums duals; empty for any other output.
     */
    static Optional<String> specialCase(Minimization minimization, int output) {
        List<Term> terms = minimization.outputs().get(output).terms();
        String name = minimization.spec().outputNames().get(output);
        boolean sumOfProducts = minimization.form() == Minimization.Form.SUM_OF_PRODUCTS;
        if (terms.isEmpty()) {
            return Optional.of(Messages.format(sumOfProducts ? "karnaugh.groups.alwaysZero"
                    : "karnaugh.groups.alwaysOneProduct", name));
        }
        if (terms.size() == 1 && terms.getFirst().mask() == 0) {
            return Optional.of(Messages.format(sumOfProducts ? "karnaugh.groups.alwaysOne"
                    : "karnaugh.groups.alwaysZeroProduct", name));
        }
        return Optional.empty();
    }

    /** The «Reglas» box, one line per rule, about the 1s or the 0s depending on the form. */
    static List<String> rules(Minimization.Form form) {
        String grouped = form == Minimization.Form.SUM_OF_PRODUCTS ? "1" : "0";
        String other = form == Minimization.Form.SUM_OF_PRODUCTS ? "0" : "1";
        return List.of(Messages.format("karnaugh.rules.1", grouped, other),
                Messages.text("karnaugh.rules.2"),
                Messages.text("karnaugh.rules.3"),
                Messages.text("karnaugh.rules.4"),
                Messages.format("karnaugh.rules.5", grouped),
                Messages.text("karnaugh.rules.6"));
    }

    /** The term of a group as written in the legend and the expression. */
    static String termText(Term term, List<String> inputNames, Minimization.Form form) {
        return text(term, inputNames, form == Minimization.Form.SUM_OF_PRODUCTS);
    }
}
