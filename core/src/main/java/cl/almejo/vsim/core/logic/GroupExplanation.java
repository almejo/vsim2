package cl.almejo.vsim.core.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Why a Karnaugh group gives its term, as data for the UI to render: per input whether it stays 0, stays 1 or changes
 * inside the group («A cambia → se va · B = 0 → B' · C = 1 → C»), the term, the cell count, and the algebra that
 * reduces the cells to the term — one step per changing input, each combining pairs with X·Y + X·Y' = X (in a
 * product of sums, the dual (X + Y)(X + Y') = X on the same terms).
 *
 * @param term      the group's term
 * @param form      sum of products (a group of 1s) or product of sums (a group of 0s)
 * @param variables one role per input, in input order
 * @param termText  the term as written ({@code B'C}, {@code (B + C')}, {@code 1} or {@code 0} for the whole map)
 * @param cellTerms the cells as full terms (every input present), in truth-table row order
 * @param steps     the algebra, one step per changing input in input order; empty for a single cell
 */
public record GroupExplanation(Term term, Minimization.Form form, List<VariableRole> variables, String termText,
                               List<Term> cellTerms, List<AlgebraStep> steps) {

    public GroupExplanation {
        variables = List.copyOf(variables);
        cellTerms = List.copyOf(cellTerms);
        steps = List.copyOf(steps);
    }

    public enum VariableBehavior {
        STAYS_ZERO,
        STAYS_ONE,
        CHANGES
    }

    /**
     * What one input does inside the group.
     *
     * @param input    the input (0 = first)
     * @param name     its name in the table
     * @param behavior stays 0, stays 1 or changes
     * @param literal  how it appears in the term ({@code B'}, {@code C}); empty when it changes (it goes away)
     */
    public record VariableRole(int input, String name, VariableBehavior behavior, String literal) {
    }

    /** One application of X·Y + X·Y' = X: {@code first} has the eliminated input at 0, {@code second} at 1. */
    public record Combination(Term first, Term second, Term result) {
    }

    /** Every pair that differs only in {@code eliminatedInput}, combined; the results feed the next step. */
    public record AlgebraStep(int eliminatedInput, List<Combination> combinations) {

        public AlgebraStep {
            combinations = List.copyOf(combinations);
        }
    }

    /** The explanation of a group of 1s (sum of products). */
    public static GroupExplanation of(Term term, TruthTableSpec spec) {
        return of(term, spec, Minimization.Form.SUM_OF_PRODUCTS);
    }

    public static GroupExplanation of(Term term, TruthTableSpec spec, Minimization.Form form) {
        int inputCount = spec.inputCount();
        long all = (1L << inputCount) - 1;
        if ((term.mask() & ~all) != 0) {
            throw new IllegalArgumentException("the term uses inputs beyond " + inputCount + ": " + term);
        }
        boolean sumOfProducts = form == Minimization.Form.SUM_OF_PRODUCTS;
        List<VariableRole> variables = new ArrayList<>();
        for (int input = 0; input < inputCount; input++) {
            String name = spec.inputNames().get(input);
            if (!term.uses(input, inputCount)) {
                variables.add(new VariableRole(input, name, VariableBehavior.CHANGES, ""));
                continue;
            }
            boolean value = term.valueOf(input, inputCount);
            String literal = sumOfProducts ? TermText.productLiteral(name, value) : TermText.sumLiteral(name, value);
            variables.add(new VariableRole(input, name,
                    value ? VariableBehavior.STAYS_ONE : VariableBehavior.STAYS_ZERO, literal));
        }
        String termText = sumOfProducts
                ? TermText.product(term, spec.inputNames())
                : TermText.sum(term, spec.inputNames());

        List<Term> cellTerms = new ArrayList<>();
        for (int row = 0; row <= all; row++) {
            if (term.covers(row)) {
                cellTerms.add(new Term(all, row));
            }
        }

        List<AlgebraStep> steps = new ArrayList<>();
        List<Term> current = cellTerms;
        for (int input = 0; input < inputCount; input++) {
            if (term.uses(input, inputCount)) {
                continue;
            }
            long bit = 1L << (inputCount - 1 - input);
            List<Combination> combinations = new ArrayList<>();
            List<Term> next = new ArrayList<>();
            for (Term first : current) {
                if ((first.value() & bit) != 0) {
                    continue;
                }
                Term second = new Term(first.mask(), first.value() | bit);
                Term result = new Term(first.mask() & ~bit, first.value());
                combinations.add(new Combination(first, second, result));
                next.add(result);
            }
            steps.add(new AlgebraStep(input, combinations));
            current = next;
        }
        return new GroupExplanation(term, form, variables, termText, cellTerms, steps);
    }

    /** How many cells the group covers (1, 2, 4, 8 or 16). */
    public int cellCount() {
        return cellTerms.size();
    }

    /** How many variables stay in the term («4 celdas: quedan 2 variables»). */
    public int remainingVariables() {
        return term.literalCount();
    }
}
