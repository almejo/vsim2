package cl.almejo.vsim.core.logic;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The truth table of the lines of «Circuito desde expresiones» (spec §3): one output per line, in line order; the
 * inputs are every variable the lines use, in natural order ({@code A, B, C, X1, X2, X10}). The names follow the
 * rules of any truth table ({@link TruthTableSpec}): unique ignoring case, 1..8 inputs and 1..8 outputs.
 */
public final class ExpressionTable {

    /** The most lines (outputs) of «Circuito desde expresiones»; tables imported from a file may have more. */
    public static final int MAXIMUM_LINES = 8;
    /** The most inputs (names) the lines may use. */
    public static final int MAXIMUM_INPUTS = 8;

    /**
     * The order of the inputs: names compared piece by piece, letters ignoring case (then with case, so the order is
     * total) and numbers by value ({@code X2} before {@code X10}).
     */
    public static final Comparator<String> NATURAL_ORDER = ExpressionTable::compareNaturally;

    private ExpressionTable() {
    }

    /**
     * The table, present only when there are no problems; the inputs found (also when there are problems, so the
     * dialog can list them); the problems of the set of lines (each line was already parsed without problems).
     */
    public record Result(Optional<TruthTableSpec> spec, List<String> inputNames, List<ExpressionProblem> problems) {

        public Result {
            inputNames = List.copyOf(inputNames);
            problems = List.copyOf(problems);
            if (spec.isPresent() != problems.isEmpty()) {
                throw new IllegalArgumentException("a table exactly when there are no problems");
            }
        }
    }

    public static Result of(String title, List<ExpressionLine> lines) {
        List<ExpressionProblem> problems = new ArrayList<>();
        if (lines.isEmpty()) {
            problems.add(ExpressionProblem.general(ExpressionProblem.NO_OUTPUTS));
        }
        if (lines.size() > MAXIMUM_LINES) {
            problems.add(ExpressionProblem.general(ExpressionProblem.TOO_MANY_OUTPUTS, lines.size(),
                    MAXIMUM_LINES));
        }
        Map<String, String> outputs = new HashMap<>();
        for (ExpressionLine line : lines) {
            if (outputs.putIfAbsent(key(line.outputName()), line.outputName()) != null) {
                problems.add(ExpressionProblem.general(ExpressionProblem.REPEATED_OUTPUT, line.outputName()));
            }
        }
        Set<String> variables = new LinkedHashSet<>();
        lines.forEach(line -> variables.addAll(line.expression().variables()));
        List<String> inputNames = variables.stream().sorted(NATURAL_ORDER).toList();
        Map<String, String> inputs = new HashMap<>();
        for (String name : inputNames) {
            if (outputs.containsKey(key(name))) {
                problems.add(ExpressionProblem.general(ExpressionProblem.OUTPUT_USED_AS_INPUT, name));
            }
            String earlier = inputs.putIfAbsent(key(name), name);
            if (earlier != null) {
                problems.add(ExpressionProblem.general(ExpressionProblem.INPUTS_DIFFER_ONLY_IN_CASE, earlier, name));
            }
        }
        if (inputNames.isEmpty()) {
            problems.add(ExpressionProblem.general(ExpressionProblem.NO_INPUTS));
        }
        if (inputNames.size() > MAXIMUM_INPUTS) {
            problems.add(ExpressionProblem.general(ExpressionProblem.TOO_MANY_INPUTS, inputNames.size(),
                    MAXIMUM_INPUTS));
        }
        if (!problems.isEmpty()) {
            return new Result(Optional.empty(), inputNames, problems);
        }
        return new Result(Optional.of(table(title, lines, inputNames)), inputNames, List.of());
    }

    private static TruthTableSpec table(String title, List<ExpressionLine> lines, List<String> inputNames) {
        int rows = 1 << inputNames.size();
        List<List<LogicValue>> outputs = new ArrayList<>();
        lines.forEach(line -> outputs.add(new ArrayList<>(rows)));
        Map<String, Boolean> values = new HashMap<>();
        for (int row = 0; row < rows; row++) {
            for (int input = 0; input < inputNames.size(); input++) {
                values.put(inputNames.get(input), (row >> (inputNames.size() - 1 - input) & 1) == 1);
            }
            for (int output = 0; output < lines.size(); output++) {
                boolean value = lines.get(output).expression().evaluate(values);
                outputs.get(output).add(value ? LogicValue.ONE : LogicValue.ZERO);
            }
        }
        List<String> outputNames = lines.stream().map(ExpressionLine::outputName).toList();
        return new TruthTableSpec(title, inputNames, outputNames, outputs);
    }

    /** How {@link TruthTableSpec} compares names. */
    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private static int compareNaturally(String first, String second) {
        List<String> firstPieces = pieces(first);
        List<String> secondPieces = pieces(second);
        for (int index = 0; index < Math.min(firstPieces.size(), secondPieces.size()); index++) {
            String firstPiece = firstPieces.get(index);
            String secondPiece = secondPieces.get(index);
            boolean firstNumber = Character.isDigit(firstPiece.charAt(0));
            boolean secondNumber = Character.isDigit(secondPiece.charAt(0));
            int comparison;
            if (firstNumber && secondNumber) {
                comparison = new BigInteger(firstPiece).compareTo(new BigInteger(secondPiece));
            } else if (firstNumber != secondNumber) {
                comparison = firstNumber ? -1 : 1;
            } else {
                comparison = firstPiece.compareToIgnoreCase(secondPiece);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        int byLength = Integer.compare(firstPieces.size(), secondPieces.size());
        return byLength != 0 ? byLength : first.compareTo(second);
    }

    /** {@code X10b} → {@code X}, {@code 10}, {@code b}: runs of digits and runs of anything else. */
    private static List<String> pieces(String name) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        for (int index = 1; index <= name.length(); index++) {
            if (index == name.length()
                    || Character.isDigit(name.charAt(index)) != Character.isDigit(name.charAt(index - 1))) {
                pieces.add(name.substring(start, index));
                start = index;
            }
        }
        return pieces;
    }
}
