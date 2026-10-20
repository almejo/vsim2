package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.Minimization.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * How the student's cover of one output (practice mode) compares with the table and with VSim's minimal cover.
 *
 * @param uncoveredRows        the rows the form must cover (1s in a sum of products, 0s in a product of sums) that no
 *                             term covers, ascending; «no importa» rows never need a cover
 * @param equivalent           whether every row that is not «no importa» gives its table value
 * @param firstDifferingRow    the lowest row that does not, when not equivalent
 * @param groupCount           the student's terms
 * @param literalCount         their literals
 * @param solutionGroupCount   the terms of {@link Minimizer}'s cover
 * @param solutionLiteralCount its literals
 */
public record PracticeResult(List<Integer> uncoveredRows, boolean equivalent, OptionalInt firstDifferingRow,
                             int groupCount, int literalCount, int solutionGroupCount, int solutionLiteralCount) {

    public PracticeResult {
        uncoveredRows = List.copyOf(uncoveredRows);
    }

    /**
     * Grades {@code terms} (the terms of the student's valid groups) as a cover of output {@code output}: a sum of
     * products is 1 exactly on the rows some term covers, a product of sums is 0 exactly there.
     */
    public static PracticeResult of(TruthTableSpec spec, int output, Form form, List<Term> terms) {
        LogicValue wanted = GroupCheck.wanted(form);
        List<Integer> uncovered = new ArrayList<>();
        OptionalInt firstDiffering = OptionalInt.empty();
        for (int row = 0; row < spec.rowCount(); row++) {
            LogicValue value = spec.value(output, row);
            boolean covered = covered(terms, row);
            if (value == wanted && !covered) {
                uncovered.add(row);
            }
            if (value != LogicValue.DONT_CARE && covered != (value == wanted) && firstDiffering.isEmpty()) {
                firstDiffering = OptionalInt.of(row);
            }
        }
        List<Term> solution = Minimizer.minimize(spec, form).outputs().get(output).terms();
        return new PracticeResult(uncovered, firstDiffering.isEmpty(), firstDiffering, terms.size(),
                literals(terms), solution.size(), literals(solution));
    }

    /**
     * Whether the cover is equivalent and no larger than VSim's: no more groups, and with as many groups no more
     * literals.
     */
    public boolean minimal() {
        if (!equivalent) {
            return false;
        }
        return groupCount < solutionGroupCount
                || groupCount == solutionGroupCount && literalCount <= solutionLiteralCount;
    }

    /**
     * The row a hint points at: a required row no term covers, preferring one only a single prime implicant covers
     * (an essential cell), the lowest first; empty when every required row is covered.
     */
    public static OptionalInt hintRow(TruthTableSpec spec, int output, Form form, List<Term> terms) {
        LogicValue wanted = GroupCheck.wanted(form);
        List<Term> primes = GroupCheck.primes(spec, output, form);
        OptionalInt lowest = OptionalInt.empty();
        for (int row = 0; row < spec.rowCount(); row++) {
            if (spec.value(output, row) != wanted || covered(terms, row)) {
                continue;
            }
            int coveringPrimes = 0;
            for (Term prime : primes) {
                if (prime.covers(row)) {
                    coveringPrimes++;
                }
            }
            if (coveringPrimes == 1) {
                return OptionalInt.of(row);
            }
            if (lowest.isEmpty()) {
                lowest = OptionalInt.of(row);
            }
        }
        return lowest;
    }

    /**
     * The group a second hint shows for {@code row}: the largest prime implicant covering it (fewest literals, then
     * lowest mask, then lowest value).
     *
     * @throws IllegalArgumentException when the row is not one the form must cover
     */
    public static Term hintGroup(TruthTableSpec spec, int output, Form form, int row) {
        if (spec.value(output, row) != GroupCheck.wanted(form)) {
            throw new IllegalArgumentException("row " + row + " needs no group in " + form);
        }
        return GroupCheck.primes(spec, output, form).stream()
                .filter(prime -> prime.covers(row))
                .min(GroupCheck.LARGEST_FIRST)
                .orElseThrow();
    }

    private static boolean covered(List<Term> terms, int row) {
        for (Term term : terms) {
            if (term.covers(row)) {
                return true;
            }
        }
        return false;
    }

    private static int literals(List<Term> terms) {
        int literals = 0;
        for (Term term : terms) {
            literals += term.literalCount();
        }
        return literals;
    }
}
