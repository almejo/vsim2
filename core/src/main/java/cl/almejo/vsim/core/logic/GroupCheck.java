package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.KarnaughLayout.GridCell;
import cl.almejo.vsim.core.logic.Minimization.Form;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Judges one group the student drew on a Karnaugh map (practice mode): whether it is a valid group, and if so whether
 * it is useful and as large as it can be. A sum of products groups the 1s, a product of sums the 0s; «no importa» cells
 * may be in a group or not.
 */
public final class GroupCheck {

    /** Largest first: fewest literals, then lowest mask, then lowest value. */
    static final Comparator<Term> LARGEST_FIRST = Comparator.comparingInt(Term::literalCount)
            .thenComparingLong(Term::mask).thenComparingLong(Term::value);

    /** The verdicts, red ones first, then amber, then green. */
    public enum Verdict {
        /** Red: the number of cells is not 1, 2, 4, 8 or 16. */
        INVALID_SIZE,
        /** Red: the cells are no Karnaugh rectangle (no term has exactly them). */
        NOT_RECTANGLE,
        /** Red: a cell holds 0 in a sum of products (1 in a product of sums). */
        INCLUDES_WRONG_VALUE,
        /** Amber: every cell it must cover is already in another valid group (a group of only X is redundant). */
        REDUNDANT,
        /** Amber: a larger group (a prime implicant strictly containing it) fits. */
        CAN_GROW,
        /** Green: a prime implicant that covers something no other group covers. */
        VALID;

        /** Whether the group is not a valid group at all (its term cannot be used). */
        public boolean isInvalid() {
            return this == INVALID_SIZE || this == NOT_RECTANGLE || this == INCLUDES_WRONG_VALUE;
        }
    }

    /**
     * The verdict on one group.
     *
     * @param verdict    the verdict
     * @param term       the group's term, present from {@link Verdict#INCLUDES_WRONG_VALUE} on
     * @param largerTerm for {@link Verdict#CAN_GROW}, the largest prime containing the group
     */
    public record Result(Verdict verdict, Optional<Term> term, Optional<Term> largerTerm) {
    }

    private GroupCheck() {
    }

    /**
     * Judges the group {@code cells} of output {@code output} against {@code otherGroups} (the student's other groups
     * of the same output and form; only the valid ones count for redundancy).
     */
    public static Result check(TruthTableSpec spec, int output, Form form, Set<GridCell> cells,
                               List<Set<GridCell>> otherGroups) {
        KarnaughLayout layout = KarnaughLayout.of(spec.inputCount());
        Optional<Result> invalid = invalidity(layout, spec, output, form, cells);
        if (invalid.isPresent()) {
            return invalid.get();
        }
        Term term = layout.term(cells).orElseThrow();
        LogicValue wanted = wanted(form);
        Set<GridCell> coveredByOthers = new HashSet<>();
        for (Set<GridCell> other : otherGroups) {
            if (invalidity(layout, spec, output, form, other).isEmpty()) {
                coveredByOthers.addAll(other);
            }
        }
        boolean redundant = true;
        for (GridCell cell : cells) {
            if (valueAt(layout, spec, output, cell) == wanted && !coveredByOthers.contains(cell)) {
                redundant = false;
                break;
            }
        }
        if (redundant) {
            return new Result(Verdict.REDUNDANT, Optional.of(term), Optional.empty());
        }
        Optional<Term> larger = primes(spec, output, form).stream()
                .filter(prime -> strictlyContains(prime, term))
                .min(LARGEST_FIRST);
        if (larger.isPresent()) {
            return new Result(Verdict.CAN_GROW, Optional.of(term), larger);
        }
        return new Result(Verdict.VALID, Optional.of(term), Optional.empty());
    }

    /**
     * Judges every group of one output and form, each against the others. A group equal to an earlier one does not
     * count against that earlier one, so of the same group drawn twice only the second is {@link Verdict#REDUNDANT}.
     */
    public static List<Result> checkAll(TruthTableSpec spec, int output, Form form, List<Set<GridCell>> groups) {
        List<Result> results = new ArrayList<>();
        for (int index = 0; index < groups.size(); index++) {
            Set<GridCell> cells = groups.get(index);
            List<Set<GridCell>> others = new ArrayList<>();
            for (int otherIndex = 0; otherIndex < groups.size(); otherIndex++) {
                Set<GridCell> other = groups.get(otherIndex);
                if (otherIndex < index || otherIndex > index && !other.equals(cells)) {
                    others.add(other);
                }
            }
            results.add(check(spec, output, form, cells, others));
        }
        return List.copyOf(results);
    }

    /** The prime implicants of the output for the form that cover at least one required cell. */
    static List<Term> primes(TruthTableSpec spec, int output, Form form) {
        LogicValue wanted = wanted(form);
        List<Integer> required = new ArrayList<>();
        List<Integer> optional = new ArrayList<>();
        for (int row = 0; row < spec.rowCount(); row++) {
            LogicValue value = spec.value(output, row);
            if (value == wanted) {
                required.add(row);
            } else if (value == LogicValue.DONT_CARE) {
                optional.add(row);
            }
        }
        return Minimizer.primeImplicants(required, optional, spec.inputCount());
    }

    /** The value a group must cover: 1 in a sum of products, 0 in a product of sums. */
    static LogicValue wanted(Form form) {
        return form == Form.SUM_OF_PRODUCTS ? LogicValue.ONE : LogicValue.ZERO;
    }

    /** The value a group must not include: 0 in a sum of products, 1 in a product of sums. */
    static LogicValue wrong(Form form) {
        return form == Form.SUM_OF_PRODUCTS ? LogicValue.ZERO : LogicValue.ONE;
    }

    private static Optional<Result> invalidity(KarnaughLayout layout, TruthTableSpec spec, int output, Form form,
                                               Set<GridCell> cells) {
        if (cells.isEmpty() || cells.size() > 16 || Integer.bitCount(cells.size()) != 1) {
            return Optional.of(new Result(Verdict.INVALID_SIZE, Optional.empty(), Optional.empty()));
        }
        Optional<Term> term = layout.term(cells);
        if (term.isEmpty()) {
            return Optional.of(new Result(Verdict.NOT_RECTANGLE, Optional.empty(), Optional.empty()));
        }
        LogicValue wrong = wrong(form);
        for (GridCell cell : cells) {
            if (valueAt(layout, spec, output, cell) == wrong) {
                return Optional.of(new Result(Verdict.INCLUDES_WRONG_VALUE, term, Optional.empty()));
            }
        }
        return Optional.empty();
    }

    private static boolean strictlyContains(Term larger, Term smaller) {
        return larger.mask() != smaller.mask() && (larger.mask() & ~smaller.mask()) == 0
                && (smaller.value() & larger.mask()) == larger.value();
    }

    private static LogicValue valueAt(KarnaughLayout layout, TruthTableSpec spec, int output, GridCell cell) {
        return spec.value(output, layout.tableRow(cell.row(), cell.column()));
    }
}
