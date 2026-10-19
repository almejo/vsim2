package cl.almejo.vsim.core.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Terms and expressions as text: {@code '} for negation, products by juxtaposition ({@code B'C}; with a name longer
 * than one character the literals are separated by {@code ·}), sums in parentheses ({@code (B + C')}); in a product
 * of sums, a bare literal next to a longer name is separated by {@code ·} too ({@code Cin·A'}). «Typed» text is for
 * «Circuito desde expresiones», where letters written together are one name: it also separates one-letter literals
 * that would merge ({@code A·B'C}, {@code B·A}).
 */
public final class TermText {

    private TermText() {
    }

    /** A product term: {@code B'C}; {@code 1} when no variable is left. */
    public static String product(Term term, List<String> inputNames) {
        return product(term, inputNames, false);
    }

    /** A product term; {@code typed} separates literals that would merge into one name ({@code A·B'C}). */
    private static String product(Term term, List<String> inputNames, boolean typed) {
        List<String> literals = literals(term, inputNames, false);
        if (literals.isEmpty()) {
            return "1";
        }
        String separator = separator(term, inputNames);
        if (!typed || !separator.isEmpty()) {
            return String.join(separator, literals);
        }
        StringBuilder product = new StringBuilder(literals.getFirst());
        for (int index = 1; index < literals.size(); index++) {
            product.append(typedSeparator(product, literals.get(index))).append(literals.get(index));
        }
        return product.toString();
    }

    /** {@code ·} when {@code left} ends in a name and {@code right} starts with one: side by side they would merge. */
    private static String typedSeparator(CharSequence left, String right) {
        boolean leftEndsInName = !left.isEmpty() && isNamePart(left.charAt(left.length() - 1));
        boolean rightStartsWithName = !right.isEmpty() && isNamePart(right.charAt(0));
        return leftEndsInName && rightStartsWithName ? "·" : "";
    }

    private static boolean isNamePart(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
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
        return expression(outputName, form, terms, inputNames, false);
    }

    /**
     * A whole output; {@code typed} writes it in the notation of «Circuito desde expresiones», where letters written
     * together are one name: adjacent literals are separated by {@code ·} unless a {@code '} or a parenthesis already
     * separates them ({@code F = A·B + A'C}, {@code F = B·A(C + D')}), so the text pasted into a line reads back as
     * the same function.
     */
    public static String expression(String outputName, Minimization.Form form, List<Term> terms,
                                     List<String> inputNames, boolean typed) {
        boolean sumOfProducts = form == Minimization.Form.SUM_OF_PRODUCTS;
        String right;
        if (terms.isEmpty()) {
            right = sumOfProducts ? "0" : "1";
        } else if (terms.size() == 1 && terms.getFirst().mask() == 0) {
            right = sumOfProducts ? "1" : "0";
        } else if (sumOfProducts) {
            List<String> products = new ArrayList<>();
            for (Term term : terms) {
                products.add(product(term, inputNames, typed));
            }
            right = String.join(" + ", products);
        } else if (terms.size() == 1) {
            right = bareSum(terms.getFirst(), inputNames);
        } else {
            StringBuilder sums = new StringBuilder();
            for (int index = 0; index < terms.size(); index++) {
                String next = sum(terms.get(index), inputNames);
                if (index > 0) {
                    String separator = sumSeparator(terms.get(index - 1), terms.get(index), inputNames);
                    sums.append(typed && separator.isEmpty() ? typedSeparator(sums, next) : separator);
                }
                sums.append(next);
            }
            right = sums.toString();
        }
        return outputName + " = " + right;
    }

    /** Output {@code output} of a minimization, with the table's names. */
    public static String expression(Minimization minimization, int output) {
        return expression(minimization, output, false);
    }

    /** Output {@code output} of a minimization as «Circuito desde expresiones» reads it ({@code F = A·B + C}). */
    public static String typedExpression(Minimization minimization, int output) {
        return expression(minimization, output, true);
    }

    private static String expression(Minimization minimization, int output, boolean typed) {
        TruthTableSpec spec = minimization.spec();
        Minimization.OutputExpression outputExpression = minimization.outputs().get(output);
        return expression(spec.outputNames().get(outputExpression.output()), minimization.form(),
                outputExpression.terms(), spec.inputNames(), typed);
    }

    /**
     * What goes between two sums of a product of sums: nothing ({@code (A + B)(C + D')}, {@code A(C + D')}), or
     * {@code ·} when one of them is a bare literal and a name is longer than one character, which would otherwise run
     * into its neighbour ({@code Cin·A'}, not {@code CinA'}).
     */
    public static String sumSeparator(Term left, Term right, List<String> inputNames) {
        boolean bare = left.literalCount() <= 1 || right.literalCount() <= 1;
        return bare && (!separator(left, inputNames).isEmpty() || !separator(right, inputNames).isEmpty()) ? "·" : "";
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
