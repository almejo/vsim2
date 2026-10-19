package cl.almejo.vsim.core.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Terms and expressions as text: {@code '} for negation, products by juxtaposition ({@code B'C}; with a name longer
 * than one character the literals are separated by {@code ·}), sums in parentheses ({@code (B + C')}).
 */
public final class TermText {

    private TermText() {
    }

    /** A product term: {@code B'C}; {@code 1} when no variable is left. */
    public static String product(Term term, List<String> inputNames) {
        List<String> literals = literals(term, inputNames, false);
        if (literals.isEmpty()) {
            return "1";
        }
        return String.join(separator(term, inputNames), literals);
    }

    /**
     * The sum term of a group of 0s: each variable complemented ({@code B = 0, C = 1 → (B + C')}); one literal goes
     * without parentheses; {@code 0} when no variable is left.
     */
    public static String sum(Term term, List<String> inputNames) {
        String bare = bareSum(term, inputNames);
        return term.literalCount() > 1 ? "(" + bare + ")" : bare;
    }

    /** A variable in a product: plain when it stays 1 ({@code B}), negated when it stays 0 ({@code B'}). */
    static String productLiteral(String name, boolean value) {
        return value ? name : name + "'";
    }

    /** A variable in the sum of a group of 0s: plain when it stays 0 ({@code B}), negated when it stays 1. */
    static String sumLiteral(String name, boolean value) {
        return value ? name + "'" : name;
    }

    /**
     * A whole output: {@code F = B'C + BD} or {@code F = (A + B)(C + D')}, with the constant conventions of
     * {@link Minimization.OutputExpression}. A product of one sum goes without parentheses.
     */
    public static String expression(String outputName, Minimization.Form form, List<Term> terms,
                                     List<String> inputNames) {
        boolean sumOfProducts = form == Minimization.Form.SUM_OF_PRODUCTS;
        String right;
        if (terms.isEmpty()) {
            right = sumOfProducts ? "0" : "1";
        } else if (terms.size() == 1 && terms.getFirst().mask() == 0) {
            right = sumOfProducts ? "1" : "0";
        } else if (sumOfProducts) {
            List<String> products = new ArrayList<>();
            for (Term term : terms) {
                products.add(product(term, inputNames));
            }
            right = String.join(" + ", products);
        } else if (terms.size() == 1) {
            right = bareSum(terms.getFirst(), inputNames);
        } else {
            StringBuilder sums = new StringBuilder();
            for (Term term : terms) {
                sums.append(sum(term, inputNames));
            }
            right = sums.toString();
        }
        return outputName + " = " + right;
    }

    /** Output {@code output} of a minimization, with the table's names. */
    public static String expression(Minimization minimization, int output) {
        TruthTableSpec spec = minimization.spec();
        Minimization.OutputExpression outputExpression = minimization.outputs().get(output);
        return expression(spec.outputNames().get(outputExpression.output()), minimization.form(),
                outputExpression.terms(), spec.inputNames());
    }

    private static String bareSum(Term term, List<String> inputNames) {
        List<String> literals = literals(term, inputNames, true);
        return literals.isEmpty() ? "0" : String.join(" + ", literals);
    }

    private static List<String> literals(Term term, List<String> inputNames, boolean sum) {
        int inputCount = inputNames.size();
        List<String> literals = new ArrayList<>();
        for (int input = 0; input < inputCount; input++) {
            if (term.uses(input, inputCount)) {
                String name = inputNames.get(input);
                boolean value = term.valueOf(input, inputCount);
                literals.add(sum ? sumLiteral(name, value) : productLiteral(name, value));
            }
        }
        return literals;
    }

    private static String separator(Term term, List<String> inputNames) {
        int inputCount = inputNames.size();
        for (int input = 0; input < inputCount; input++) {
            if (term.uses(input, inputCount) && inputNames.get(input).length() > 1) {
                return "·";
            }
        }
        return "";
    }
}
