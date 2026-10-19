package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.Minimization.Form;
import cl.almejo.vsim.core.logic.Minimization.OutputExpression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Two-level minimization of a truth table: Quine–McCluskey prime implicants over the 1s (sum of products) or the 0s
 * (product of sums), «no importa» rows as don't cares, then a minimum cover by branch and bound (fewest terms, then
 * fewest literals).
 * <p>
 * Determinism: the primes and rows are put in a canonical order before the search, so the result depends only on the
 * table. Among covers of equal cost the first one found wins; the search tries rows with the fewest covering primes
 * first (then the lowest row) and, for each row, the primes with the fewest literals first (then by the first row they
 * cover and their mask). The cover is exact whenever the search finishes within {@link #NODE_BUDGET} nodes, which is
 * always the case up to 4 inputs; beyond that budget (large, irregular tables of 7 or 8 inputs) the best cover found
 * so far is kept, which is still correct, only perhaps not the smallest.
 */
public final class Minimizer {

    /** Search nodes of the branch and bound before keeping the best cover found so far. */
    static final int NODE_BUDGET = 20_000;

    /** Canonical term order: by the first row the term covers (its value), then by mask. */
    static final Comparator<Term> CANONICAL = Comparator.comparingLong(Term::value).thenComparingLong(Term::mask);

    private Minimizer() {
    }

    public static Minimization minimize(TruthTableSpec spec, Form form) {
        LogicValue grouped = form == Form.SUM_OF_PRODUCTS ? LogicValue.ONE : LogicValue.ZERO;
        List<OutputExpression> outputs = new ArrayList<>();
        for (int output = 0; output < spec.outputCount(); output++) {
            List<Integer> required = new ArrayList<>();
            List<Integer> optional = new ArrayList<>();
            for (int row = 0; row < spec.rowCount(); row++) {
                LogicValue value = spec.value(output, row);
                if (value == grouped) {
                    required.add(row);
                } else if (value == LogicValue.DONT_CARE) {
                    optional.add(row);
                }
            }
            List<Term> primes = primeImplicants(required, optional, spec.inputCount());
            outputs.add(new OutputExpression(output, cover(primes, required)));
        }
        return new Minimization(spec, form, outputs);
    }

    /**
     * The prime implicants of the rows {@code required} with the don't cares {@code optional}, keeping only those that
     * cover at least one required row, in canonical order.
     */
    static List<Term> primeImplicants(List<Integer> required, List<Integer> optional, int inputCount) {
        if (required.isEmpty()) {
            return List.of();
        }
        long fullMask = (1L << inputCount) - 1;
        Set<Term> current = new HashSet<>();
        for (int row : required) {
            current.add(new Term(fullMask, row));
        }
        for (int row : optional) {
            current.add(new Term(fullMask, row));
        }
        Set<Term> primes = new TreeSet<>(CANONICAL);
        while (!current.isEmpty()) {
            Set<Term> next = new HashSet<>();
            Set<Term> combined = new HashSet<>();
            for (Term term : current) {
                for (int bit = 0; bit < inputCount; bit++) {
                    long bitMask = 1L << bit;
                    if ((term.mask() & bitMask) == 0 || (term.value() & bitMask) != 0) {
                        continue;
                    }
                    Term partner = new Term(term.mask(), term.value() | bitMask);
                    if (current.contains(partner)) {
                        next.add(new Term(term.mask() & ~bitMask, term.value()));
                        combined.add(term);
                        combined.add(partner);
                    }
                }
            }
            for (Term term : current) {
                if (!combined.contains(term)) {
                    primes.add(term);
                }
            }
            current = next;
        }
        List<Term> useful = new ArrayList<>();
        for (Term prime : primes) {
            for (int row : required) {
                if (prime.covers(row)) {
                    useful.add(prime);
                    break;
                }
            }
        }
        return useful;
    }

    /** A minimum cover of {@code required} by {@code primes}, in canonical order; independent of the lists' order. */
    static List<Term> cover(List<Term> primes, List<Integer> required) {
        Set<Term> canonicalPrimes = new TreeSet<>(CANONICAL);
        canonicalPrimes.addAll(primes);
        List<Term> sortedPrimes = new ArrayList<>(canonicalPrimes);
        List<Integer> rows = new ArrayList<>(new TreeSet<>(required));
        if (rows.isEmpty()) {
            return List.of();
        }
        CoverSearch search = new CoverSearch(sortedPrimes, rows);
        List<Term> chosen = new ArrayList<>();
        for (int primeIndex : search.solve()) {
            chosen.add(sortedPrimes.get(primeIndex));
        }
        chosen.sort(CANONICAL);
        return List.copyOf(chosen);
    }

    /** Branch and bound over a covering table: rows to cover, columns the primes. */
    private static final class CoverSearch {

        private final List<Term> primes;
        private final int rowCount;
        /** Per row, the primes covering it, fewest literals first then canonical. */
        private final int[][] primesOfRow;
        /** Per prime, the rows it covers. */
        private final int[][] rowsOfPrime;
        /** Rows in the order they are branched on: fewest covering primes first, then lowest row. */
        private final int[] rowOrder;
        private final int[] coverCount;
        private final boolean[] blockedPrimes;
        private int nodes;
        private int bestTerms = Integer.MAX_VALUE;
        private int bestLiterals = Integer.MAX_VALUE;
        private int[] best;

        CoverSearch(List<Term> primes, List<Integer> rows) {
            this.primes = primes;
            this.rowCount = rows.size();
            Comparator<Integer> branchOrder = Comparator.<Integer>comparingInt(index -> primes.get(index).literalCount())
                    .thenComparingInt(index -> index);
            primesOfRow = new int[rowCount][];
            List<List<Integer>> rowsByPrime = new ArrayList<>();
            for (int primeIndex = 0; primeIndex < primes.size(); primeIndex++) {
                rowsByPrime.add(new ArrayList<>());
            }
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                List<Integer> covering = new ArrayList<>();
                for (int primeIndex = 0; primeIndex < primes.size(); primeIndex++) {
                    if (primes.get(primeIndex).covers(rows.get(rowIndex))) {
                        covering.add(primeIndex);
                        rowsByPrime.get(primeIndex).add(rowIndex);
                    }
                }
                if (covering.isEmpty()) {
                    throw new IllegalArgumentException("row " + rows.get(rowIndex) + " is not covered by any prime");
                }
                covering.sort(branchOrder);
                primesOfRow[rowIndex] = covering.stream().mapToInt(Integer::intValue).toArray();
            }
            rowsOfPrime = new int[primes.size()][];
            for (int primeIndex = 0; primeIndex < primes.size(); primeIndex++) {
                rowsOfPrime[primeIndex] = rowsByPrime.get(primeIndex).stream().mapToInt(Integer::intValue).toArray();
            }
            List<Integer> order = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                order.add(rowIndex);
            }
            order.sort(Comparator.<Integer>comparingInt(rowIndex -> primesOfRow[rowIndex].length)
                    .thenComparingInt(rowIndex -> rowIndex));
            rowOrder = order.stream().mapToInt(Integer::intValue).toArray();
            coverCount = new int[rowCount];
            blockedPrimes = new boolean[primes.size()];
        }

        int[] solve() {
            List<Integer> chosen = new ArrayList<>();
            int terms = 0;
            int literals = 0;
            // Essential primes: the only prime covering some row is in every cover.
            for (int rowIndex : rowOrder) {
                if (primesOfRow[rowIndex].length == 1 && coverCount[rowIndex] == 0) {
                    int primeIndex = primesOfRow[rowIndex][0];
                    take(primeIndex);
                    chosen.add(primeIndex);
                    terms++;
                    literals += primes.get(primeIndex).literalCount();
                }
            }
            greedy(new ArrayList<>(chosen), terms, literals);
            branch(chosen, terms, literals);
            return best;
        }

        /** An initial cover to bound the search: repeatedly the prime covering most open rows. */
        private void greedy(List<Integer> chosen, int terms, int literals) {
            int[] counts = coverCount.clone();
            while (true) {
                int bestPrime = -1;
                int bestGain = 0;
                for (int primeIndex = 0; primeIndex < primes.size(); primeIndex++) {
                    int gain = 0;
                    for (int rowIndex : rowsOfPrime[primeIndex]) {
                        if (counts[rowIndex] == 0) {
                            gain++;
                        }
                    }
                    if (gain > bestGain || gain == bestGain && gain > 0
                            && primes.get(primeIndex).literalCount() < primes.get(bestPrime).literalCount()) {
                        bestPrime = primeIndex;
                        bestGain = gain;
                    }
                }
                if (bestPrime < 0) {
                    break;
                }
                for (int rowIndex : rowsOfPrime[bestPrime]) {
                    counts[rowIndex]++;
                }
                chosen.add(bestPrime);
                terms++;
                literals += primes.get(bestPrime).literalCount();
            }
            record(chosen, terms, literals);
        }

        private void branch(List<Integer> chosen, int terms, int literals) {
            int open = firstOpenRow();
            if (open < 0) {
                if (terms < bestTerms || terms == bestTerms && literals < bestLiterals) {
                    record(chosen, terms, literals);
                }
                return;
            }
            if (nodes++ >= NODE_BUDGET) {
                return;
            }
            int[] bound = lowerBound();
            if (isNotBetter(terms + bound[0], literals + bound[1])) {
                return;
            }
            for (int primeIndex : primesOfRow[open]) {
                take(primeIndex);
                chosen.add(primeIndex);
                branch(chosen, terms + 1, literals + primes.get(primeIndex).literalCount());
                chosen.removeLast();
                release(primeIndex);
                if (nodes >= NODE_BUDGET) {
                    return;
                }
            }
        }

        private boolean isNotBetter(int terms, int literals) {
            return terms > bestTerms || terms == bestTerms && literals >= bestLiterals;
        }

        private int firstOpenRow() {
            for (int rowIndex : rowOrder) {
                if (coverCount[rowIndex] == 0) {
                    return rowIndex;
                }
            }
            return -1;
        }

        /**
         * Open rows no two of which share a prime each need their own term: their count bounds the terms still
         * needed, and the sum of their cheapest primes' literals bounds the literals of a cover with that many terms.
         */
        private int[] lowerBound() {
            Arrays.fill(blockedPrimes, false);
            int terms = 0;
            int literals = 0;
            for (int rowIndex : rowOrder) {
                if (coverCount[rowIndex] != 0) {
                    continue;
                }
                boolean independent = true;
                for (int primeIndex : primesOfRow[rowIndex]) {
                    if (blockedPrimes[primeIndex]) {
                        independent = false;
                        break;
                    }
                }
                if (!independent) {
                    continue;
                }
                int cheapest = Integer.MAX_VALUE;
                for (int primeIndex : primesOfRow[rowIndex]) {
                    blockedPrimes[primeIndex] = true;
                    cheapest = Math.min(cheapest, primes.get(primeIndex).literalCount());
                }
                terms++;
                literals += cheapest;
            }
            return new int[] {terms, literals};
        }

        private void take(int primeIndex) {
            for (int rowIndex : rowsOfPrime[primeIndex]) {
                coverCount[rowIndex]++;
            }
        }

        private void release(int primeIndex) {
            for (int rowIndex : rowsOfPrime[primeIndex]) {
                coverCount[rowIndex]--;
            }
        }

        private void record(List<Integer> chosen, int terms, int literals) {
            bestTerms = terms;
            bestLiterals = literals;
            best = chosen.stream().mapToInt(Integer::intValue).toArray();
        }
    }
}
