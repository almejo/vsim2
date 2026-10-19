package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.Expression;
import cl.almejo.vsim.core.logic.ExpressionLine;
import cl.almejo.vsim.core.logic.ExpressionParser;
import cl.almejo.vsim.core.logic.ExpressionProblem;
import cl.almejo.vsim.core.logic.ExpressionTable;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TermText;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What «Circuito desde expresiones» edits (spec §3), headless, on the EDT: the title, the lines as typed (1..8, each
 * parsed again when it changes), «Simplificar», the step-4 options and the merged-letter names the user dismissed.
 * The table ({@link #spec()}) exists when every line reads and the set of lines has no problem. Listeners hear every
 * change.
 */
final class ExpressionsModel {

    static final int MAXIMUM_LINES = TruthTableSpec.MAXIMUM_OUTPUTS;

    /** The names a new line proposes, in order; a name already used (ignoring case) is skipped. */
    private static final String PROPOSED_NAMES = "FGHIJKLMNOPQRSTUVWXYZABCDE";

    /** What a line the dialog proposed («F = ») has until the user types its expression. */
    private static final Set<String> EMPTY_PROBLEMS =
            Set.of(ExpressionProblem.EMPTY_EXPRESSION, ExpressionProblem.EMPTY_LINE);

    /** What closing compares: the dialog asks before discarding only when these differ from the start. */
    record Contents(String title, List<String> lines) {
        Contents {
            lines = List.copyOf(lines);
        }
    }

    private final List<Runnable> listeners = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final List<ExpressionParser.ParseResult> parsed = new ArrayList<>();
    /** Per line, whether the user changed it: an untouched «G = » does not show that it misses its expression. */
    private final List<Boolean> edited = new ArrayList<>();
    private final Set<String> dismissed = new HashSet<>();
    private String title = Messages.text("expressions.defaultTitle");
    private boolean simplify;
    private WizardOptions options = WizardOptions.defaults();
    private ExpressionTable.Result table;

    ExpressionsModel(boolean simplify) {
        this.simplify = simplify;
        lines.add(proposedLine());
        parsed.add(ExpressionParser.parseLine(lines.getFirst()));
        edited.add(false);
        update();
    }

    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    String title() {
        return title;
    }

    void setTitle(String newTitle) {
        String next = newTitle == null ? "" : newTitle;
        if (!next.equals(title)) {
            title = next;
            changed();
        }
    }

    List<String> lines() {
        return List.copyOf(lines);
    }

    int lineCount() {
        return lines.size();
    }

    void setLine(int index, String text) {
        if (!text.equals(lines.get(index))) {
            lines.set(index, text);
            parsed.set(index, ExpressionParser.parseLine(text));
            edited.set(index, true);
            changed();
        }
    }

    boolean canAddLine() {
        return lines.size() < MAXIMUM_LINES;
    }

    /** Adds {@code «G = »} (the next free name) at the end; false when there are already eight lines. */
    boolean addLine() {
        if (!canAddLine()) {
            return false;
        }
        String line = proposedLine();
        lines.add(line);
        parsed.add(ExpressionParser.parseLine(line));
        edited.add(false);
        changed();
        return true;
    }

    boolean canRemoveLine() {
        return lines.size() > 1;
    }

    /** Removes line {@code index}; the last line stays. */
    void removeLine(int index) {
        if (canRemoveLine()) {
            lines.remove(index);
            parsed.remove(index);
            edited.remove(index);
            changed();
        }
    }

    boolean simplify() {
        return simplify;
    }

    void setSimplify(boolean newSimplify) {
        if (newSimplify != simplify) {
            simplify = newSimplify;
            changed();
        }
    }

    WizardOptions options() {
        return options;
    }

    void setOptions(WizardOptions newOptions) {
        if (!newOptions.equals(options)) {
            options = newOptions;
            changed();
        }
    }

    /**
     * Why line {@code index} does not read, ready to show («Falta cerrar un paréntesis (posición 9)»); a line the user
     * has not edited yet does not complain that it is empty (the table still waits for it).
     */
    Optional<String> lineProblem(int index) {
        return parsed.get(index).problem()
                .filter(problem -> edited.get(index) || !EMPTY_PROBLEMS.contains(problem.messageKey()))
                .map(ExpressionsModel::describe);
    }

    /** The first name of line {@code index} that looks like letters written together and was not dismissed. */
    Optional<String> mergedWarning(int index) {
        return parsed.get(index).mergedNames().stream().filter(name -> !dismissed.contains(name)).findFirst();
    }

    /** ✕ on the warning: {@code name} is meant as one name, for the rest of this dialog. */
    void dismissMerged(String name) {
        if (dismissed.add(name)) {
            changed();
        }
    }

    /** «¿Quisiste decir A·B·C? Sepáralas con espacio o *». */
    static String mergedText(String name) {
        List<String> letters = new ArrayList<>();
        name.chars().forEach(letter -> letters.add(Character.toString(letter)));
        return Messages.format("expressions.merged", String.join("·", letters));
    }

    /** The inputs found: those of every line that reads, in natural order (also while another line is wrong). */
    List<String> inputNames() {
        if (table != null) {
            return table.inputNames();
        }
        Set<String> names = new LinkedHashSet<>();
        for (ExpressionParser.ParseResult result : parsed) {
            result.line().ifPresent(line -> names.addAll(line.expression().variables()));
        }
        return names.stream().sorted(ExpressionTable.NATURAL_ORDER).toList();
    }

    /** «Entradas: A, B, Cin (3)», or «Entradas: ninguna». */
    String inputsText() {
        List<String> names = inputNames();
        if (names.isEmpty()) {
            return Messages.text("expressions.inputs.none");
        }
        return Messages.format("expressions.inputs", String.join(", ", names), names.size());
    }

    /** The problems of the set of lines (a repeated output, too many inputs), once every line reads. */
    List<String> tableProblems() {
        return table == null ? List.of() : table.problems().stream().map(ExpressionsModel::describe).toList();
    }

    /** The lines read, when every one of them reads. */
    Optional<List<ExpressionLine>> expressionLines() {
        if (parsed.stream().anyMatch(result -> result.line().isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(parsed.stream().map(result -> result.line().orElseThrow()).toList());
    }

    /** The truth table of the lines, when they have no problem at all. */
    Optional<TruthTableSpec> spec() {
        return table == null ? Optional.empty() : table.spec();
    }

    /** Whether «Ver en Karnaugh…» can take the table: 2..4 inputs and up to 4 outputs, no problems. */
    boolean canOpenInWizard() {
        return spec().filter(spec -> spec.inputCount() >= KarnaughModel.MINIMUM_INPUTS
                && spec.inputCount() <= KarnaughModel.MAXIMUM_INPUTS
                && spec.outputCount() <= KarnaughModel.MAXIMUM_OUTPUTS).isPresent();
    }

    Contents contents() {
        return new Contents(title, lines);
    }

    /**
     * What goes under a line while simplifying: «→ F = A'B + C», written so that this dialog reads it back
     * ({@code A·B}, not {@code AB}), or «ya está simplificada» when the line is already a sum of products (a product of
     * sums in that form) with exactly the minimized terms, none repeated.
     */
    static String previewText(ExpressionLine line, Minimization minimization, int output) {
        List<Term> terms = minimization.outputs().get(output).terms();
        Optional<List<Term>> written = termsOf(line.expression(), minimization.form(),
                minimization.spec().inputNames());
        if (written.isPresent() && written.get().size() == terms.size()
                && new HashSet<>(written.get()).equals(new HashSet<>(terms))) {
            return Messages.text("expressions.preview.simplified");
        }
        return Messages.format("expressions.preview.simplifies", TermText.typedExpression(minimization, output));
    }

    /**
     * The terms {@code expression} is written as, in the {@link Term} convention of {@code form}: a sum of products
     * (or one product, or a constant) for a sum of products, a product of sums for a product of sums, in the order
     * written and with any repeats. Empty when it is written otherwise, or a term names a variable twice.
     */
    static Optional<List<Term>> termsOf(Expression expression, Minimization.Form form, List<String> inputNames) {
        boolean sumOfProducts = form == Minimization.Form.SUM_OF_PRODUCTS;
        if (expression instanceof Expression.Constant constant) {
            // A sum of products: 0 has no terms, 1 is the term of every row; a product of sums the other way round.
            return Optional.of(constant.value() == sumOfProducts ? List.of(new Term(0, 0)) : List.of());
        }
        List<Expression> terms = sumOfProducts
                ? expression instanceof Expression.Or or ? or.operands() : List.of(expression)
                : expression instanceof Expression.And and ? and.operands() : List.of(expression);
        List<Term> result = new ArrayList<>();
        for (Expression term : terms) {
            List<Expression> literals = sumOfProducts
                    ? term instanceof Expression.And and ? and.operands() : List.of(term)
                    : term instanceof Expression.Or or ? or.operands() : List.of(term);
            long mask = 0;
            long value = 0;
            for (Expression literal : literals) {
                boolean plain = literal instanceof Expression.Variable;
                Expression variable = plain ? literal
                        : literal instanceof Expression.Not not ? not.operand() : null;
                if (!(variable instanceof Expression.Variable named)) {
                    return Optional.empty();
                }
                int input = inputNames.indexOf(named.name());
                long bit = 1L << (inputNames.size() - 1 - input);
                if (input < 0 || (mask & bit) != 0) {
                    return Optional.empty();
                }
                mask |= bit;
                // A product keeps the inputs that stay 1 plain; the sum of a group of 0s negates those that stay 1.
                if (plain == sumOfProducts) {
                    value |= bit;
                }
            }
            result.add(new Term(mask, value));
        }
        return Optional.of(result);
    }

    /** A problem's message with its position when it has one: «Falta cerrar un paréntesis (posición 9)». */
    static String describe(ExpressionProblem problem) {
        String message = Messages.format(problem.messageKey(), problem.arguments().toArray());
        if (problem.column() < 0) {
            return message;
        }
        String bare = message.endsWith(".") ? message.substring(0, message.length() - 1) : message;
        return Messages.format("expressions.position", bare, String.valueOf(problem.column() + 1));
    }

    private String proposedLine() {
        Set<String> used = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int equals = line.indexOf('=');
            used.add((equals < 0 ? line : line.substring(0, equals)).strip().toLowerCase(Locale.ROOT));
            parsed.get(index).line().ifPresent(read -> read.expression().variables()
                    .forEach(name -> used.add(name.toLowerCase(Locale.ROOT))));
        }
        for (char letter : PROPOSED_NAMES.toCharArray()) {
            String name = String.valueOf(letter);
            if (!used.contains(name.toLowerCase(Locale.ROOT))) {
                return name + " = ";
            }
        }
        return "";
    }

    private void update() {
        Optional<List<ExpressionLine>> read = expressionLines();
        String effectiveTitle = title.isBlank() ? Messages.text("expressions.defaultTitle") : title.strip();
        table = read.map(each -> ExpressionTable.of(effectiveTitle, each)).orElse(null);
    }

    private void changed() {
        update();
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
