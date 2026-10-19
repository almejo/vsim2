package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.Minimization.Form;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MinimizerTest {

    private static final List<String> NAMES = List.of("A", "B", "C", "D", "E", "F", "G", "H");

    @Test
    void theBcdExampleGivesTheExpressionsOfTheTalk() {
        // P: the digit is prime (2, 3, 5, 7); M: the digit is at least 5. Rows 10..15 are not BCD digits.
        TruthTableSpec spec = new TruthTableSpec("BCD", List.of("A", "B", "C", "D"), List.of("P", "M"),
                List.of(column(4, List.of(2, 3, 5, 7), List.of(10, 11, 12, 13, 14, 15)),
                        column(4, List.of(5, 6, 7, 8, 9), List.of(10, 11, 12, 13, 14, 15))));

        Minimization minimization = Minimizer.minimize(spec, Form.SUM_OF_PRODUCTS);

        assertThat(minimization.spec()).isEqualTo(spec);
        assertThat(minimization.form()).isEqualTo(Form.SUM_OF_PRODUCTS);
        // B'C = (mask B C, B = 0, C = 1), BD = (mask B D, both 1); sorted by the first row they cover.
        assertThat(minimization.outputs().get(0).terms())
                .containsExactly(new Term(0b0110, 0b0010), new Term(0b0101, 0b0101));
        // M = A + BD + BC: BD covers row 5 first, BC row 6, A row 8.
        assertThat(minimization.outputs().get(1).terms())
                .containsExactly(new Term(0b0101, 0b0101), new Term(0b0110, 0b0110), new Term(0b1000, 0b1000));
        assertThat(minimization.outputs().get(0).output()).isZero();
        assertThat(minimization.outputs().get(1).output()).isEqualTo(1);
    }

    @Test
    void productOfSumsGroupsTheZeros() {
        TruthTableSpec spec = new TruthTableSpec("BCD", List.of("A", "B", "C", "D"), List.of("P"),
                List.of(column(4, List.of(2, 3, 5, 7), List.of(10, 11, 12, 13, 14, 15))));

        Minimization minimization = Minimizer.minimize(spec, Form.PRODUCT_OF_SUMS);

        assertThat(minimization.form()).isEqualTo(Form.PRODUCT_OF_SUMS);
        List<Term> terms = minimization.outputs().get(0).terms();
        // Zeros: 0, 1, 4, 6, 8, 9. P = (B + C)(B' + D): groups B = 0, C = 0 and B = 1, D = 0.
        assertThat(terms).containsExactly(new Term(0b0110, 0b0000), new Term(0b0101, 0b0100));
        assertProductOfSumsEquivalent(spec, 0, terms);
    }

    @Test
    void constantOutputsFollowTheDocumentedConventions() {
        List<String> inputs = List.of("A", "B", "C");
        TruthTableSpec spec = new TruthTableSpec("T", inputs, List.of("Z", "O", "X", "OX"),
                List.of(column(3, List.of(), List.of()),
                        column(3, List.of(0, 1, 2, 3, 4, 5, 6, 7), List.of()),
                        column(3, List.of(), List.of(0, 1, 2, 3, 4, 5, 6, 7)),
                        column(3, List.of(3), List.of(0, 1, 2, 4, 5, 6, 7))));

        Minimization sum = Minimizer.minimize(spec, Form.SUM_OF_PRODUCTS);
        assertThat(sum.outputs().get(0).terms()).isEmpty();
        assertThat(sum.outputs().get(1).terms()).containsExactly(new Term(0, 0));
        assertThat(sum.outputs().get(2).terms()).isEmpty();
        assertThat(sum.outputs().get(3).terms()).containsExactly(new Term(0, 0));

        Minimization product = Minimizer.minimize(spec, Form.PRODUCT_OF_SUMS);
        assertThat(product.outputs().get(0).terms()).containsExactly(new Term(0, 0));
        assertThat(product.outputs().get(1).terms()).isEmpty();
        assertThat(product.outputs().get(2).terms()).isEmpty();
        assertThat(product.outputs().get(3).terms()).isEmpty();
    }

    @Test
    void oneInputTablesWork() {
        TruthTableSpec spec = new TruthTableSpec("T", List.of("A"), List.of("F", "G"),
                List.of(column(1, List.of(1), List.of()), column(1, List.of(0), List.of())));

        Minimization minimization = Minimizer.minimize(spec, Form.SUM_OF_PRODUCTS);

        assertThat(minimization.outputs().get(0).terms()).containsExactly(new Term(1, 1));
        assertThat(minimization.outputs().get(1).terms()).containsExactly(new Term(1, 0));
    }

    @Test
    void everyFunctionOfTwoInputsWithDontCaresIsMinimal() {
        int rows = 4;
        int functions = (int) Math.pow(3, rows);
        for (int code = 0; code < functions; code++) {
            List<LogicValue> values = new ArrayList<>();
            int remaining = code;
            for (int row = 0; row < rows; row++) {
                values.add(LogicValue.values()[remaining % 3]);
                remaining /= 3;
            }
            checkAgainstBruteForce(values, 2);
        }
    }

    @Test
    void everyFunctionOfThreeInputsIsMinimal() {
        for (int code = 0; code < 256; code++) {
            List<LogicValue> values = new ArrayList<>();
            for (int row = 0; row < 8; row++) {
                values.add((code >> row & 1) == 1 ? LogicValue.ONE : LogicValue.ZERO);
            }
            checkAgainstBruteForce(values, 3);
        }
    }

    @Test
    void randomFourInputFunctionsWithDontCaresHaveTheMinimumPrimeCover() {
        Random random = new Random(20261018L);
        for (int sample = 0; sample < 300; sample++) {
            List<LogicValue> values = randomValues(random, 16, sample % 4 == 0 ? 0.0 : 0.25);
            for (Form form : Form.values()) {
                TruthTableSpec spec = spec(4, values);
                List<Term> terms = Minimizer.minimize(spec, form).outputs().get(0).terms();
                LogicValue covered = form == Form.SUM_OF_PRODUCTS ? LogicValue.ONE : LogicValue.ZERO;
                assertCoversExactly(values, covered, terms);
                int[] best = ExhaustivePrimeCover.minimum(values, covered, 4);
                assertThat(new int[] {terms.size(), literalCount(terms)})
                        .as("sample %d %s %s", sample, form, values)
                        .containsExactly(best);
            }
        }
    }

    @Test
    void randomLargerFunctionsAreEquivalentAndFast() {
        Random random = new Random(42L);
        for (int inputs = 5; inputs <= 8; inputs++) {
            for (int sample = 0; sample < 12; sample++) {
                List<LogicValue> values = randomValues(random, 1 << inputs, sample % 3 == 0 ? 0.0 : 0.2);
                TruthTableSpec spec = spec(inputs, values);
                for (Form form : Form.values()) {
                    long start = System.nanoTime();
                    List<Term> terms = Minimizer.minimize(spec, form).outputs().get(0).terms();
                    long elapsedMilliseconds = (System.nanoTime() - start) / 1_000_000;
                    LogicValue covered = form == Form.SUM_OF_PRODUCTS ? LogicValue.ONE : LogicValue.ZERO;
                    assertCoversExactly(values, covered, terms);
                    assertThat(elapsedMilliseconds).as("%d inputs, sample %d, %s", inputs, sample, form)
                            .isLessThan(200);
                }
            }
        }
    }

    @Test
    void anEightByEightTableMinimizesEveryOutput() {
        Random random = new Random(7L);
        List<List<LogicValue>> outputs = new ArrayList<>();
        for (int output = 0; output < 8; output++) {
            outputs.add(randomValues(random, 256, 0.1));
        }
        TruthTableSpec spec = new TruthTableSpec("T", NAMES, List.of("P", "Q", "R", "S", "T", "U", "V", "W"),
                outputs);

        long start = System.nanoTime();
        Minimization minimization = Minimizer.minimize(spec, Form.SUM_OF_PRODUCTS);
        long elapsedMilliseconds = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMilliseconds).isLessThan(1600);
        assertThat(minimization.outputs()).hasSize(8);
        for (int output = 0; output < 8; output++) {
            assertThat(minimization.outputs().get(output).output()).isEqualTo(output);
            assertCoversExactly(outputs.get(output), LogicValue.ONE, minimization.outputs().get(output).terms());
        }
    }

    @Test
    void theSameTableAlwaysGivesTheSameTerms() {
        Random random = new Random(99L);
        for (int sample = 0; sample < 40; sample++) {
            int inputs = 2 + sample % 5;
            TruthTableSpec spec = spec(inputs, randomValues(random, 1 << inputs, 0.3));
            for (Form form : Form.values()) {
                assertThat(Minimizer.minimize(spec, form)).isEqualTo(Minimizer.minimize(spec, form));
            }
        }
    }

    @Test
    void theCoverDoesNotDependOnTheOrderOfThePrimeImplicants() {
        Random random = new Random(1234L);
        for (int sample = 0; sample < 100; sample++) {
            int inputs = 3 + sample % 4;
            List<LogicValue> values = randomValues(random, 1 << inputs, 0.25);
            List<Integer> required = new ArrayList<>();
            List<Integer> optional = new ArrayList<>();
            for (int row = 0; row < values.size(); row++) {
                if (values.get(row) == LogicValue.ONE) {
                    required.add(row);
                } else if (values.get(row) == LogicValue.DONT_CARE) {
                    optional.add(row);
                }
            }
            List<Term> primes = Minimizer.primeImplicants(required, optional, inputs);
            List<Term> expected = Minimizer.cover(primes, required);
            for (int shuffle = 0; shuffle < 5; shuffle++) {
                List<Term> shuffled = new ArrayList<>(primes);
                Collections.shuffle(shuffled, random);
                List<Integer> shuffledRows = new ArrayList<>(required);
                Collections.shuffle(shuffledRows, random);
                assertThat(Minimizer.cover(shuffled, shuffledRows)).isEqualTo(expected);
            }
        }
    }

    @Test
    void termsAreSortedByTheFirstRowTheyCover() {
        Random random = new Random(5L);
        for (int sample = 0; sample < 50; sample++) {
            int inputs = 2 + sample % 6;
            TruthTableSpec spec = spec(inputs, randomValues(random, 1 << inputs, 0.2));
            List<Term> terms = Minimizer.minimize(spec, Form.SUM_OF_PRODUCTS).outputs().get(0).terms();
            for (int index = 1; index < terms.size(); index++) {
                Term previous = terms.get(index - 1);
                Term current = terms.get(index);
                assertThat(previous.value() < current.value()
                        || previous.value() == current.value() && previous.mask() < current.mask()).isTrue();
            }
        }
    }

    // ----- helpers -----

    private static void checkAgainstBruteForce(List<LogicValue> values, int inputs) {
        TruthTableSpec spec = spec(inputs, values);
        for (Form form : Form.values()) {
            LogicValue covered = form == Form.SUM_OF_PRODUCTS ? LogicValue.ONE : LogicValue.ZERO;
            List<Term> terms = Minimizer.minimize(spec, form).outputs().get(0).terms();
            assertCoversExactly(values, covered, terms);
            int[] best = bruteForceMinimum(values, covered, inputs);
            assertThat(new int[] {terms.size(), literalCount(terms)}).as("%s %s", form, values)
                    .containsExactly(best);
        }
    }

    /** The minimum (term count, literal count) over every set of implicants, primes or not. */
    private static int[] bruteForceMinimum(List<LogicValue> values, LogicValue covered, int inputs) {
        LogicValue avoided = covered == LogicValue.ONE ? LogicValue.ZERO : LogicValue.ONE;
        List<Integer> required = new ArrayList<>();
        for (int row = 0; row < values.size(); row++) {
            if (values.get(row) == covered) {
                required.add(row);
            }
        }
        if (required.isEmpty()) {
            return new int[] {0, 0};
        }
        List<Term> implicants = new ArrayList<>();
        for (Term term : allTerms(inputs)) {
            boolean valid = true;
            for (int row = 0; row < values.size(); row++) {
                if (term.covers(row) && values.get(row) == avoided) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                implicants.add(term);
            }
        }
        for (int size = 1; size <= implicants.size(); size++) {
            int bestLiterals = Integer.MAX_VALUE;
            int[] chosen = new int[size];
            bestLiterals = combinations(implicants, required, chosen, 0, 0, bestLiterals);
            if (bestLiterals != Integer.MAX_VALUE) {
                return new int[] {size, bestLiterals};
            }
        }
        throw new AssertionError("no cover");
    }

    private static int combinations(List<Term> implicants, List<Integer> required, int[] chosen, int depth,
                                    int start, int bestLiterals) {
        if (depth == chosen.length) {
            int literals = 0;
            for (int index : chosen) {
                literals += implicants.get(index).literalCount();
            }
            if (literals >= bestLiterals) {
                return bestLiterals;
            }
            for (int row : required) {
                boolean rowCovered = false;
                for (int index : chosen) {
                    if (implicants.get(index).covers(row)) {
                        rowCovered = true;
                        break;
                    }
                }
                if (!rowCovered) {
                    return bestLiterals;
                }
            }
            return literals;
        }
        for (int index = start; index < implicants.size(); index++) {
            chosen[depth] = index;
            bestLiterals = combinations(implicants, required, chosen, depth + 1, index + 1, bestLiterals);
        }
        return bestLiterals;
    }

    static List<Term> allTerms(int inputs) {
        List<Term> terms = new ArrayList<>();
        int full = (1 << inputs) - 1;
        for (int mask = 0; mask <= full; mask++) {
            for (int value = 0; value <= full; value++) {
                if ((value & ~mask) == 0) {
                    terms.add(new Term(mask, value));
                }
            }
        }
        return terms;
    }

    /** Every covered row is in some term, and no term touches a row of the opposite value. */
    private static void assertCoversExactly(List<LogicValue> values, LogicValue covered, List<Term> terms) {
        LogicValue avoided = covered == LogicValue.ONE ? LogicValue.ZERO : LogicValue.ONE;
        for (int row = 0; row < values.size(); row++) {
            boolean inSomeTerm = false;
            for (Term term : terms) {
                if (term.covers(row)) {
                    inSomeTerm = true;
                    break;
                }
            }
            if (values.get(row) == covered) {
                assertThat(inSomeTerm).as("row %d must be covered", row).isTrue();
            } else if (values.get(row) == avoided) {
                assertThat(inSomeTerm).as("row %d must not be covered", row).isFalse();
            }
        }
    }

    /** The product of the sum terms (each the complement of its group of zeros) equals the table off X. */
    private static void assertProductOfSumsEquivalent(TruthTableSpec spec, int output, List<Term> terms) {
        for (int row = 0; row < spec.rowCount(); row++) {
            boolean product = true;
            for (Term term : terms) {
                product &= !term.covers(row);
            }
            LogicValue expected = spec.value(output, row);
            if (expected != LogicValue.DONT_CARE) {
                assertThat(product).as("row %d", row).isEqualTo(expected == LogicValue.ONE);
            }
        }
    }

    private static int literalCount(List<Term> terms) {
        int literals = 0;
        for (Term term : terms) {
            literals += term.literalCount();
        }
        return literals;
    }

    private static List<LogicValue> randomValues(Random random, int rows, double dontCareProbability) {
        List<LogicValue> values = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            double draw = random.nextDouble();
            values.add(draw < dontCareProbability ? LogicValue.DONT_CARE
                    : random.nextBoolean() ? LogicValue.ONE : LogicValue.ZERO);
        }
        return values;
    }

    private static TruthTableSpec spec(int inputs, List<LogicValue> values) {
        return new TruthTableSpec("T", NAMES.subList(0, inputs), List.of("OUT"), List.of(values));
    }

    private static List<LogicValue> column(int inputs, List<Integer> ones, List<Integer> dontCares) {
        List<LogicValue> values = new ArrayList<>(Collections.nCopies(1 << inputs, LogicValue.ZERO));
        ones.forEach(row -> values.set(row, LogicValue.ONE));
        dontCares.forEach(row -> values.set(row, LogicValue.DONT_CARE));
        return values;
    }

    /** An independent exhaustive search: primes by definition, then every cover branching on the first open row. */
    private static final class ExhaustivePrimeCover {

        private int bestTerms;
        private int bestLiterals;

        static int[] minimum(List<LogicValue> values, LogicValue covered, int inputs) {
            LogicValue avoided = covered == LogicValue.ONE ? LogicValue.ZERO : LogicValue.ONE;
            List<Integer> required = new ArrayList<>();
            for (int row = 0; row < values.size(); row++) {
                if (values.get(row) == covered) {
                    required.add(row);
                }
            }
            if (required.isEmpty()) {
                return new int[] {0, 0};
            }
            List<Term> implicants = new ArrayList<>();
            for (Term term : allTerms(inputs)) {
                boolean valid = true;
                for (int row = 0; row < values.size(); row++) {
                    if (term.covers(row) && values.get(row) == avoided) {
                        valid = false;
                    }
                }
                if (valid) {
                    implicants.add(term);
                }
            }
            List<Term> primes = new ArrayList<>();
            for (Term candidate : implicants) {
                boolean prime = true;
                for (Term other : implicants) {
                    boolean contains = !other.equals(candidate) && (other.mask() & ~candidate.mask()) == 0
                            && (candidate.value() & other.mask()) == other.value();
                    if (contains) {
                        prime = false;
                        break;
                    }
                }
                if (prime) {
                    primes.add(candidate);
                }
            }
            ExhaustivePrimeCover search = new ExhaustivePrimeCover();
            search.bestTerms = Integer.MAX_VALUE;
            search.bestLiterals = Integer.MAX_VALUE;
            search.search(primes, required, new ArrayList<>());
            return new int[] {search.bestTerms, search.bestLiterals};
        }

        private void search(List<Term> primes, List<Integer> required, List<Term> chosen) {
            int open = -1;
            for (int row : required) {
                boolean rowCovered = false;
                for (Term term : chosen) {
                    if (term.covers(row)) {
                        rowCovered = true;
                        break;
                    }
                }
                if (!rowCovered) {
                    open = row;
                    break;
                }
            }
            if (open < 0) {
                int literals = literalCount(chosen);
                if (chosen.size() < bestTerms || chosen.size() == bestTerms && literals < bestLiterals) {
                    bestTerms = chosen.size();
                    bestLiterals = literals;
                }
                return;
            }
            if (chosen.size() >= bestTerms) {
                return;
            }
            for (Term prime : primes) {
                if (prime.covers(open)) {
                    chosen.add(prime);
                    search(primes, required, chosen);
                    chosen.removeLast();
                }
            }
        }
    }
}
