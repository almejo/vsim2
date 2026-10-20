package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.KarnaughLayout;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization;
import cl.almejo.vsim.core.logic.Minimizer;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TruthTableSpec;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the Karnaugh wizard edits (spec §3), on the EDT: the title, the input and output names as typed (they may be
 * blank or repeated while the user types; {@link #problem()} says why the table cannot go on), the output values and
 * the step-4 options. Every later step derives from it: {@link #spec()} and {@link #minimization()} (computed again
 * after every change, cheap for 4 inputs). Listeners hear every change.
 */
public final class KarnaughModel {

    public static final int MINIMUM_INPUTS = KarnaughLayout.MINIMUM_INPUTS;
    public static final int MAXIMUM_INPUTS = KarnaughLayout.MAXIMUM_INPUTS;
    public static final int MINIMUM_OUTPUTS = 1;
    public static final int MAXIMUM_OUTPUTS = 4;
    static final int DEFAULT_INPUTS = 3;

    private static final List<String> DEFAULT_INPUT_NAMES = List.of("A", "B", "C", "D");
    private static final List<String> DEFAULT_OUTPUT_NAMES = List.of("F", "G", "H", "I");
    /** When a default name is taken, the letters after it are tried in order (G taken → H). */
    private static final String SPARE_NAMES = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final List<Runnable> listeners = new ArrayList<>();
    private String title;
    private final List<String> inputNames = new ArrayList<>();
    private final List<String> outputNames = new ArrayList<>();
    /** Per output, one value per row (row bits: the first input is the most significant). */
    private final List<List<LogicValue>> values = new ArrayList<>();
    private WizardOptions options = WizardOptions.defaults();
    private int selectedOutput;
    private Minimization minimization;
    private final PracticeModel practice = new PracticeModel(this);
    private boolean useStudentGroups = true;

    public KarnaughModel() {
        title = Messages.text("karnaugh.table.defaultTitle");
        inputNames.addAll(DEFAULT_INPUT_NAMES.subList(0, DEFAULT_INPUTS));
        outputNames.add(DEFAULT_OUTPUT_NAMES.getFirst());
        values.add(new ArrayList<>(Collections.nCopies(rowCount(), LogicValue.ZERO)));
        practice.tableChanged(values, outputNames);
        practice.addListener(this::fire);
    }

    /** The student's groups of the practice mode (step 3); its changes reach this model's listeners too. */
    public PracticeModel practice() {
        return practice;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** Replaces everything with {@code spec} (2..4 inputs, 1..4 outputs), keeping the options. */
    public void load(TruthTableSpec spec) {
        if (spec.inputCount() < MINIMUM_INPUTS || spec.inputCount() > MAXIMUM_INPUTS
                || spec.outputCount() > MAXIMUM_OUTPUTS) {
            throw new IllegalArgumentException("the wizard takes 2..4 inputs and 1..4 outputs");
        }
        title = spec.title();
        inputNames.clear();
        inputNames.addAll(spec.inputNames());
        outputNames.clear();
        outputNames.addAll(spec.outputNames());
        values.clear();
        for (List<LogicValue> column : spec.outputs()) {
            values.add(new ArrayList<>(column));
        }
        selectedOutput = 0;
        changed();
    }

    public String title() {
        return title;
    }

    public void setTitle(String newTitle) {
        if (!newTitle.equals(title)) {
            title = newTitle;
            changed();
        }
    }

    public int inputCount() {
        return inputNames.size();
    }

    public int outputCount() {
        return outputNames.size();
    }

    public int rowCount() {
        return 1 << inputNames.size();
    }

    public List<String> inputNames() {
        return List.copyOf(inputNames);
    }

    public List<String> outputNames() {
        return List.copyOf(outputNames);
    }

    /**
     * Adds or removes inputs at the end. The function is kept: an added input is one it does not depend on (both of
     * its rows take the old value), a removed one keeps the rows where it was 0.
     */
    public void setInputCount(int count) {
        int clamped = Math.clamp(count, MINIMUM_INPUTS, MAXIMUM_INPUTS);
        int oldCount = inputNames.size();
        if (clamped == oldCount) {
            return;
        }
        while (inputNames.size() < clamped) {
            inputNames.add(freeName(DEFAULT_INPUT_NAMES.get(inputNames.size())));
        }
        while (inputNames.size() > clamped) {
            inputNames.removeLast();
        }
        for (int output = 0; output < values.size(); output++) {
            List<LogicValue> old = values.get(output);
            List<LogicValue> resized = new ArrayList<>();
            for (int row = 0; row < 1 << clamped; row++) {
                int oldRow = clamped > oldCount ? row >> (clamped - oldCount) : row << (oldCount - clamped);
                resized.add(old.get(oldRow));
            }
            values.set(output, resized);
        }
        changed();
    }

    /** Adds outputs at 0 or removes them from the end. */
    public void setOutputCount(int count) {
        int clamped = Math.clamp(count, MINIMUM_OUTPUTS, MAXIMUM_OUTPUTS);
        if (clamped == outputNames.size()) {
            return;
        }
        while (outputNames.size() < clamped) {
            outputNames.add(freeName(DEFAULT_OUTPUT_NAMES.get(outputNames.size())));
            values.add(new ArrayList<>(Collections.nCopies(rowCount(), LogicValue.ZERO)));
        }
        while (outputNames.size() > clamped) {
            outputNames.removeLast();
            values.removeLast();
        }
        selectedOutput = Math.min(selectedOutput, clamped - 1);
        changed();
    }

    public void setInputName(int input, String name) {
        if (!name.equals(inputNames.get(input))) {
            inputNames.set(input, name);
            changed();
        }
    }

    public void setOutputName(int output, String name) {
        if (!name.equals(outputNames.get(output))) {
            outputNames.set(output, name);
            changed();
        }
    }

    public LogicValue value(int output, int row) {
        return values.get(output).get(row);
    }

    public void setValue(int output, int row, LogicValue value) {
        if (values.get(output).get(row) != value) {
            values.get(output).set(row, value);
            changed();
        }
    }

    /** 0 → 1 → X → 0, the click on an output cell. */
    public void cycle(int output, int row) {
        setValue(output, row, value(output, row).next());
    }

    public WizardOptions options() {
        return options;
    }

    public void setOptions(WizardOptions newOptions) {
        if (!newOptions.equals(options)) {
            options = newOptions;
            changed();
        }
    }

    /** The output steps 2 and 3 show (their tabs). */
    public int selectedOutput() {
        return selectedOutput;
    }

    public void setSelectedOutput(int output) {
        int clamped = Math.clamp(output, 0, outputNames.size() - 1);
        if (clamped != selectedOutput) {
            selectedOutput = clamped;
            fire();
        }
    }

    /** Why the table cannot go past step 1 (a blank title, a blank or repeated name), or empty when it can. */
    public Optional<String> problem() {
        if (title.isBlank()) {
            return Optional.of(Messages.text("karnaugh.problem.title"));
        }
        for (int input = 0; input < inputNames.size(); input++) {
            if (inputNames.get(input).isBlank()) {
                return Optional.of(Messages.format("karnaugh.problem.blankInput", String.valueOf(input + 1)));
            }
        }
        for (int output = 0; output < outputNames.size(); output++) {
            if (outputNames.get(output).isBlank()) {
                return Optional.of(Messages.format("karnaugh.problem.blankOutput", String.valueOf(output + 1)));
            }
        }
        Set<String> seen = new HashSet<>();
        List<String> all = new ArrayList<>(inputNames);
        all.addAll(outputNames);
        for (String name : all) {
            if (!seen.add(key(name))) {
                return Optional.of(Messages.format("karnaugh.problem.repeated", name.strip()));
            }
        }
        return Optional.empty();
    }

    /** The table as a spec, names stripped; only when {@link #problem()} is empty. */
    public TruthTableSpec spec() {
        if (problem().isPresent()) {
            throw new IllegalStateException("the table has a problem: " + problem().get());
        }
        return new TruthTableSpec(title.strip(), inputNames.stream().map(String::strip).toList(),
                outputNames.stream().map(String::strip).toList(), values);
    }

    /** The minimized table in the chosen form; only when {@link #problem()} is empty. */
    public Minimization minimization() {
        if (minimization == null) {
            minimization = Minimizer.minimize(spec(), options.form());
        }
        return minimization;
    }

    /**
     * What steps 4 and 5 build: {@link #minimization()}, except that while {@link #usingStudentGroups()} every output
     * the student practised in the chosen form takes the terms of their groups that are not red (in drawing order);
     * the outputs not practised keep VSim's terms.
     */
    public Minimization effectiveMinimization() {
        Minimization base = minimization();
        if (!usingStudentGroups()) {
            return base;
        }
        Minimization.Form form = options.form();
        List<Minimization.OutputExpression> outputs = new ArrayList<>();
        for (Minimization.OutputExpression expression : base.outputs()) {
            int output = expression.output();
            outputs.add(practice.practised(output, form)
                    ? new Minimization.OutputExpression(output, practice.validTerms(output, form))
                    : expression);
        }
        return new Minimization(base.spec(), form, outputs);
    }

    /**
     * What step 4 shows (its expressions and colours): {@link #effectiveMinimization()}, except that in the practice
     * wizard an output whose solution was not revealed yet («Revisar» or «Me rindo» in step 3) shows the student's
     * terms (their groups that are not red, possibly none), so VSim's answer never shows before the student asks.
     */
    Minimization shownMinimization() {
        Minimization effective = effectiveMinimization();
        if (!practice.practising()) {
            return effective;
        }
        Minimization.Form form = options.form();
        List<Minimization.OutputExpression> outputs = new ArrayList<>();
        for (Minimization.OutputExpression expression : effective.outputs()) {
            int output = expression.output();
            outputs.add(practice.revealed(output, form) ? expression
                    : new Minimization.OutputExpression(output, practice.validTerms(output, form)));
        }
        return new Minimization(effective.spec(), form, outputs);
    }

    /**
     * Whether step 4 says «F: todavía sin grupos» for output {@code output}: in the practice wizard, not revealed, no
     * groups drawn in the chosen form, and the table needs some (an output that needs none shows its constant).
     */
    boolean waitingForGroups(int output) {
        Minimization.Form form = options.form();
        return practice.practising() && !practice.revealed(output, form) && !practice.practised(output, form)
                && !practice.result(output, form).equivalent();
    }

    /**
     * Whether steps 4 and 5 use the student's groups: «Usar mis grupos» is on (it is by default), this is the practice
     * wizard, some output was practised in the chosen form and {@link #studentGroupsProblem()} is empty.
     */
    public boolean usingStudentGroups() {
        return useStudentGroups && anyPractised() && studentGroupsProblem().isEmpty();
    }

    /** «Usar mis grupos» checked or not; it only counts while the student's groups are allowed. */
    public void setUseStudentGroups(boolean use) {
        if (use != useStudentGroups) {
            useStudentGroups = use;
            fire();
        }
    }

    /**
     * Whether some output was practised in the chosen form, in the practice wizard (step 4 shows «Usar mis grupos» only
     * then).
     */
    public boolean anyPractised() {
        if (!practice.practising()) {
            return false;
        }
        for (int output = 0; output < outputNames.size(); output++) {
            if (practice.practised(output, options.form())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Why the student's groups cannot be used: the first practised output whose cover is not equivalent to the table
     * («F: tus grupos no cubren la tabla»); empty when they can (or when nothing was practised).
     */
    public Optional<String> studentGroupsProblem() {
        Minimization.Form form = options.form();
        for (int output = 0; output < outputNames.size(); output++) {
            if (practice.practised(output, form) && !practice.result(output, form).equivalent()) {
                return Optional.of(Messages.format("karnaugh.practice.notCovered", outputNames.get(output).strip()));
            }
        }
        return Optional.empty();
    }

    /**
     * The palette colour of term {@code term} of output {@code output}. Without sharing, the groups of each output
     * count from the first colour. With sharing, a term has one colour on every map it is on (a shared group looks
     * the same everywhere), and no two groups of one map share a colour: going through the distinct terms in table
     * order, each takes the first palette colour that no term already coloured on any of its maps uses. Over VSim's
     * {@link #minimization()} (step 3's solution).
     */
    public int colorIndex(int output, int term) {
        return colorIndex(minimization(), output, term);
    }

    /** {@link #colorIndex(int, int)} over {@code minimization} (step 4 colours the {@link #effectiveMinimization()}). */
    int colorIndex(Minimization minimization, int output, int term) {
        if (!options.shareTerms()) {
            return term;
        }
        List<Minimization.OutputExpression> expressions = minimization.outputs();
        Map<Term, Integer> colours = new HashMap<>();
        for (Minimization.OutputExpression expression : expressions) {
            for (Term each : expression.terms()) {
                if (colours.containsKey(each)) {
                    continue;
                }
                Set<Integer> taken = new HashSet<>();
                for (Minimization.OutputExpression map : expressions) {
                    if (map.terms().contains(each)) {
                        for (Term neighbour : map.terms()) {
                            Integer colour = colours.get(neighbour);
                            if (colour != null) {
                                taken.add(Math.floorMod(colour, GroupPalette.size()));
                            }
                        }
                    }
                }
                int colour = 0;
                while (colour < GroupPalette.size() && taken.contains(colour)) {
                    colour++;
                }
                colours.put(each, colour < GroupPalette.size() ? colour : colours.size());
            }
        }
        return colours.get(expressions.get(output).terms().get(term));
    }

    /**
     * The other outputs that use term {@code term} of output {@code output} when sharing is on (one gate feeds them
     * all), in table order; empty without sharing, for a constant, and for a single literal (a wire, not a gate).
     */
    public List<String> sharedWith(int output, int term) {
        Term shared = minimization().outputs().get(output).terms().get(term);
        if (!options.shareTerms() || shared.literalCount() <= 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        List<Minimization.OutputExpression> expressions = minimization().outputs();
        for (int other = 0; other < expressions.size(); other++) {
            if (other != output && expressions.get(other).terms().contains(shared)) {
                names.add(outputNames.get(other).strip());
            }
        }
        return names;
    }

    private String freeName(String preferred) {
        Set<String> taken = new HashSet<>();
        inputNames.forEach(name -> taken.add(key(name)));
        outputNames.forEach(name -> taken.add(key(name)));
        if (!taken.contains(key(preferred))) {
            return preferred;
        }
        int start = Math.max(0, SPARE_NAMES.indexOf(preferred));
        for (int offset = 1; offset < SPARE_NAMES.length(); offset++) {
            String candidate = String.valueOf(SPARE_NAMES.charAt((start + offset) % SPARE_NAMES.length()));
            if (!taken.contains(key(candidate))) {
                return candidate;
            }
        }
        return preferred;
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    /** What the user edits by hand (title, names, counts, values), to tell whether it changed; options are left out. */
    record Contents(String title, List<String> inputNames, List<String> outputNames, List<List<LogicValue>> values) {
    }

    Contents contents() {
        return new Contents(title, List.copyOf(inputNames), List.copyOf(outputNames),
                values.stream().map(List::copyOf).toList());
    }

    private void changed() {
        minimization = null;
        practice.tableChanged(values, outputNames);
        fire();
    }

    private void fire() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
