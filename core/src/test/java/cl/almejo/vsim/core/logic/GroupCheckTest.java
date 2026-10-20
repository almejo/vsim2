package cl.almejo.vsim.core.logic;

import cl.almejo.vsim.core.logic.GroupCheck.Result;
import cl.almejo.vsim.core.logic.GroupCheck.Verdict;
import cl.almejo.vsim.core.logic.KarnaughLayout.GridCell;
import cl.almejo.vsim.core.logic.Minimization.Form;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GroupCheckTest {

    private static final List<String> NAMES = List.of("A", "B", "C", "D");

    @Test
    void everyDrawnRectangleOfEveryMapAgreesWithABruteForceForRandomFunctionsWithDontCares() {
        Random random = new Random(20261019L);
        Set<Verdict> seen = EnumSet.noneOf(Verdict.class);
        for (int inputCount = 2; inputCount <= 4; inputCount++) {
            KarnaughLayout layout = KarnaughLayout.of(inputCount);
            List<Set<GridCell>> rectangles = everyDrawnRectangle(layout);
            for (int sample = 0; sample < 200; sample++) {
                TruthTableSpec spec = spec(inputCount, randomValues(random, 1 << inputCount, sample % 5 == 0 ? 0.0
                        : 0.25));
                for (Form form : Form.values()) {
                    for (Set<GridCell> cells : rectangles) {
                        List<Set<GridCell>> others = new ArrayList<>();
                        int otherCount = random.nextInt(4);
                        for (int index = 0; index < otherCount; index++) {
                            others.add(rectangles.get(random.nextInt(rectangles.size())));
                        }
                        Result actual = GroupCheck.check(spec, 0, form, cells, others);
                        Result expected = bruteForce(layout, spec, form, cells, others);
                        assertThat(actual).as("%d inputs, %s, %s, cells %s, others %s, values %s", inputCount, form,
                                sample, cells, others, spec.outputs().get(0)).isEqualTo(expected);
                        seen.add(actual.verdict());
                    }
                }
            }
        }
        // A drawn rectangle of a power-of-two size is always a subcube: only the core check can say NOT_RECTANGLE.
        assertThat(seen).isEqualTo(EnumSet.complementOf(EnumSet.of(Verdict.NOT_RECTANGLE)));
    }

    @Test
    void anInvalidSizeWinsOverAWrongValue() {
        // 3 inputs, all 0: three cells of row 0 hold 0s, but the size is reported first.
        TruthTableSpec spec = spec(3, Collections.nCopies(8, LogicValue.ZERO));
        Set<GridCell> cells = KarnaughLayout.of(3).drawn(0, 0, 1, 3).cells();

        Result result = GroupCheck.check(spec, 0, Form.SUM_OF_PRODUCTS, cells, List.of());

        assertThat(result.verdict()).isEqualTo(Verdict.INVALID_SIZE);
        assertThat(result.term()).isEmpty();
        assertThat(result.largerTerm()).isEmpty();
    }

    @Test
    void fourCellsThatAreNoRectangleAreReportedAsSuch() {
        TruthTableSpec spec = spec(4, Collections.nCopies(16, LogicValue.ONE));
        Set<GridCell> diagonal = Set.of(new GridCell(0, 0), new GridCell(1, 1), new GridCell(2, 2),
                new GridCell(3, 3));

        Result result = GroupCheck.check(spec, 0, Form.SUM_OF_PRODUCTS, diagonal, List.of());

        assertThat(result.verdict()).isEqualTo(Verdict.NOT_RECTANGLE);
        assertThat(result.term()).isEmpty();
    }

    @Test
    void aZeroInASumOfProductsGroupAndAOneInAProductOfSumsGroupAreWrongValues() {
        // 2 inputs, F = A: rows 2 and 3 are 1. The top row (A = 0) has 0s.
        TruthTableSpec spec = spec(2, List.of(LogicValue.ZERO, LogicValue.ZERO, LogicValue.ONE, LogicValue.ONE));
        KarnaughLayout layout = KarnaughLayout.of(2);

        Result leftColumn = GroupCheck.check(spec, 0, Form.SUM_OF_PRODUCTS, layout.drawn(0, 0, 2, 1).cells(),
                List.of());
        Result bottomRowInProductOfSums = GroupCheck.check(spec, 0, Form.PRODUCT_OF_SUMS,
                layout.drawn(1, 0, 1, 2).cells(), List.of());

        assertThat(leftColumn.verdict()).isEqualTo(Verdict.INCLUDES_WRONG_VALUE);
        assertThat(leftColumn.term()).contains(new Term(0b01, 0b00));
        assertThat(bottomRowInProductOfSums.verdict()).isEqualTo(Verdict.INCLUDES_WRONG_VALUE);
        assertThat(bottomRowInProductOfSums.term()).contains(new Term(0b10, 0b10));
    }

    @Test
    void aGroupOfOnlyDontCaresIsRedundant() {
        TruthTableSpec spec = spec(2, List.of(LogicValue.DONT_CARE, LogicValue.ZERO, LogicValue.ONE, LogicValue.ONE));

        Result result = GroupCheck.check(spec, 0, Form.SUM_OF_PRODUCTS, Set.of(new GridCell(0, 0)), List.of());

        assertThat(result.verdict()).isEqualTo(Verdict.REDUNDANT);
        assertThat(result.term()).contains(new Term(0b11, 0b00));
    }

    @Test
    void aGroupWhoseOnesAreInOtherValidGroupsIsRedundantButNotWhenTheOthersAreRed() {
        // 2 inputs, F = A + B: rows 1, 2, 3.
        TruthTableSpec spec = spec(2, List.of(LogicValue.ZERO, LogicValue.ONE, LogicValue.ONE, LogicValue.ONE));
        KarnaughLayout layout = KarnaughLayout.of(2);
        Set<GridCell> bottomRow = layout.drawn(1, 0, 1, 2).cells();
        Set<GridCell> rightColumn = layout.drawn(0, 1, 2, 1).cells();
        Set<GridCell> wholeMap = layout.drawn(0, 0, 2, 2).cells();
        Set<GridCell> corner = Set.of(new GridCell(1, 1));

        assertThat(GroupCheck.check(spec, 0, Form.SUM_OF_PRODUCTS, corner, List.of(bottomRow, rightColumn))
                .verdict()).isEqualTo(Verdict.REDUNDANT);
        // The whole map includes a 0: it does not count as covering the corner.
        assertThat(GroupCheck.check(spec, 0, Form.SUM_OF_PRODUCTS, corner, List.of(wholeMap)).verdict())
                .isEqualTo(Verdict.CAN_GROW);
    }

    @Test
    void aGroupThatFitsInALargerPrimeCanGrowToTheLargestOne() {
        // 4 inputs, all 1 except row 0: the single cell 15 fits in A, B, C and D (8 cells, 1 literal each); the tie
        // goes to the lowest mask, D (mask 0001).
        List<LogicValue> values = new ArrayList<>(Collections.nCopies(16, LogicValue.ONE));
        values.set(0, LogicValue.ZERO);
        TruthTableSpec spec = spec(4, values);
        Set<GridCell> cell = Set.of(KarnaughLayout.of(4).cell(15));

        Result result = GroupCheck.check(spec, 0, Form.SUM_OF_PRODUCTS, cell, List.of());

        assertThat(result.verdict()).isEqualTo(Verdict.CAN_GROW);
        assertThat(result.term()).contains(new Term(0b1111, 0b1111));
        assertThat(result.largerTerm()).contains(new Term(0b0001, 0b0001));
    }

    @Test
    void aPrimeThatCoversSomethingAloneIsValid() {
        // 3 inputs, F = A: the bottom row.
        TruthTableSpec spec = spec(3, List.of(LogicValue.ZERO, LogicValue.ZERO, LogicValue.ZERO, LogicValue.ZERO,
                LogicValue.ONE, LogicValue.ONE, LogicValue.ONE, LogicValue.ONE));

        Result result = GroupCheck.check(spec, 0, Form.SUM_OF_PRODUCTS, KarnaughLayout.of(3).drawn(1, 0, 1, 4).cells(),
                List.of());

        assertThat(result).isEqualTo(new Result(Verdict.VALID, Optional.of(new Term(0b100, 0b100)), Optional.empty()));
    }

    @Test
    void theSameGroupTwiceMakesOnlyTheSecondRedundant() {
        TruthTableSpec spec = spec(3, List.of(LogicValue.ZERO, LogicValue.ZERO, LogicValue.ZERO, LogicValue.ZERO,
                LogicValue.ONE, LogicValue.ONE, LogicValue.ONE, LogicValue.ONE));
        Set<GridCell> bottomRow = KarnaughLayout.of(3).drawn(1, 0, 1, 4).cells();

        List<Result> results = GroupCheck.checkAll(spec, 0, Form.SUM_OF_PRODUCTS, List.of(bottomRow, bottomRow));

        assertThat(results).extracting(Result::verdict).containsExactly(Verdict.VALID, Verdict.REDUNDANT);
    }

    @Test
    void checkAllJudgesEachGroupAgainstTheOthers() {
        // 2 inputs, F = A + B: A, B and the corner AB; the corner is redundant, A and B are needed.
        TruthTableSpec spec = spec(2, List.of(LogicValue.ZERO, LogicValue.ONE, LogicValue.ONE, LogicValue.ONE));
        KarnaughLayout layout = KarnaughLayout.of(2);
        List<Set<GridCell>> groups = List.of(layout.drawn(1, 0, 1, 2).cells(), Set.of(new GridCell(1, 1)),
                layout.drawn(0, 1, 2, 1).cells(), layout.drawn(0, 0, 1, 2).cells());

        List<Result> results = GroupCheck.checkAll(spec, 0, Form.SUM_OF_PRODUCTS, groups);

        assertThat(results).extracting(Result::verdict).containsExactly(Verdict.VALID, Verdict.REDUNDANT,
                Verdict.VALID, Verdict.INCLUDES_WRONG_VALUE);
    }

    @Test
    void theWholeMapOfAConstantOneIsValid() {
        TruthTableSpec spec = spec(4, Collections.nCopies(16, LogicValue.ONE));

        Result result = GroupCheck.check(spec, 0, Form.SUM_OF_PRODUCTS, KarnaughLayout.of(4).drawn(0, 0, 4, 4).cells(),
                List.of());

        assertThat(result.verdict()).isEqualTo(Verdict.VALID);
        assertThat(result.term()).contains(new Term(0, 0));
    }

    /** An independent judge: subcubes, implicants and primes found by enumerating every term and every row. */
    private static Result bruteForce(KarnaughLayout layout, TruthTableSpec spec, Form form, Set<GridCell> cells,
                                     List<Set<GridCell>> others) {
        int size = cells.size();
        if (size != 1 && size != 2 && size != 4 && size != 8 && size != 16) {
            return new Result(Verdict.INVALID_SIZE, Optional.empty(), Optional.empty());
        }
        Optional<Term> term = subcube(layout, cells);
        if (term.isEmpty()) {
            return new Result(Verdict.NOT_RECTANGLE, Optional.empty(), Optional.empty());
        }
        LogicValue wanted = form == Form.SUM_OF_PRODUCTS ? LogicValue.ONE : LogicValue.ZERO;
        LogicValue wrong = form == Form.SUM_OF_PRODUCTS ? LogicValue.ZERO : LogicValue.ONE;
        if (holds(layout, spec, cells, wrong)) {
            return new Result(Verdict.INCLUDES_WRONG_VALUE, term, Optional.empty());
        }
        Set<GridCell> coveredByOthers = new HashSet<>();
        for (Set<GridCell> other : others) {
            int otherSize = other.size();
            boolean validSize = otherSize == 1 || otherSize == 2 || otherSize == 4 || otherSize == 8
                    || otherSize == 16;
            if (validSize && subcube(layout, other).isPresent() && !holds(layout, spec, other, wrong)) {
                coveredByOthers.addAll(other);
            }
        }
        boolean redundant = true;
        for (GridCell cell : cells) {
            if (valueAt(layout, spec, cell) == wanted && !coveredByOthers.contains(cell)) {
                redundant = false;
            }
        }
        if (redundant) {
            return new Result(Verdict.REDUNDANT, term, Optional.empty());
        }
        Term best = null;
        int all = (1 << layout.inputCount()) - 1;
        for (int mask = 0; mask <= all; mask++) {
            for (int value = 0; value <= all; value++) {
                if ((value & ~mask) != 0) {
                    continue;
                }
                Term candidate = new Term(mask, value);
                boolean strictlyContains = (candidate.mask() & ~term.get().mask()) == 0
                        && (term.get().value() & candidate.mask()) == candidate.value()
                        && candidate.mask() != term.get().mask();
                if (!strictlyContains) {
                    continue;
                }
                boolean implicant = true;
                for (int row = 0; row <= all; row++) {
                    if (candidate.covers(row) && spec.value(0, row) == wrong) {
                        implicant = false;
                    }
                }
                if (implicant && (best == null || isBetter(candidate, best))) {
                    best = candidate;
                }
            }
        }
        if (best != null) {
            return new Result(Verdict.CAN_GROW, term, Optional.of(best));
        }
        return new Result(Verdict.VALID, term, Optional.empty());
    }

    private static boolean isBetter(Term candidate, Term best) {
        if (candidate.literalCount() != best.literalCount()) {
            return candidate.literalCount() < best.literalCount();
        }
        if (candidate.mask() != best.mask()) {
            return candidate.mask() < best.mask();
        }
        return candidate.value() < best.value();
    }

    private static Optional<Term> subcube(KarnaughLayout layout, Set<GridCell> cells) {
        int all = (1 << layout.inputCount()) - 1;
        for (int mask = 0; mask <= all; mask++) {
            for (int value = 0; value <= all; value++) {
                if ((value & ~mask) != 0) {
                    continue;
                }
                Set<GridCell> covered = new HashSet<>();
                for (int row = 0; row <= all; row++) {
                    if ((row & mask) == value) {
                        covered.add(layout.cell(row));
                    }
                }
                if (covered.equals(cells)) {
                    return Optional.of(new Term(mask, value));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean holds(KarnaughLayout layout, TruthTableSpec spec, Set<GridCell> cells, LogicValue value) {
        for (GridCell cell : cells) {
            if (valueAt(layout, spec, cell) == value) {
                return true;
            }
        }
        return false;
    }

    private static LogicValue valueAt(KarnaughLayout layout, TruthTableSpec spec, GridCell cell) {
        return spec.value(0, layout.tableRow(cell.row(), cell.column()));
    }

    private static List<Set<GridCell>> everyDrawnRectangle(KarnaughLayout layout) {
        List<Set<GridCell>> rectangles = new ArrayList<>();
        for (int firstRow = 0; firstRow < layout.rows(); firstRow++) {
            for (int firstColumn = 0; firstColumn < layout.columns(); firstColumn++) {
                for (int rowSpan = 1; rowSpan <= layout.rows(); rowSpan++) {
                    for (int columnSpan = 1; columnSpan <= layout.columns(); columnSpan++) {
                        rectangles.add(layout.drawn(firstRow, firstColumn, rowSpan, columnSpan).cells());
                    }
                }
            }
        }
        layout.corners().ifPresent(corners -> rectangles.add(corners.cells()));
        return rectangles;
    }

    static List<LogicValue> randomValues(Random random, int rows, double dontCareProbability) {
        List<LogicValue> values = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            double draw = random.nextDouble();
            values.add(draw < dontCareProbability ? LogicValue.DONT_CARE
                    : random.nextBoolean() ? LogicValue.ONE : LogicValue.ZERO);
        }
        return values;
    }

    static TruthTableSpec spec(int inputCount, List<LogicValue> values) {
        return new TruthTableSpec("T", NAMES.subList(0, inputCount), List.of("F"), List.of(values));
    }
}
