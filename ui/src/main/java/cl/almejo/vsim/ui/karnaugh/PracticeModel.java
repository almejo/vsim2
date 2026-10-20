package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.core.logic.DrawnGroup;
import cl.almejo.vsim.core.logic.GroupCheck;
import cl.almejo.vsim.core.logic.KarnaughLayout.GridCell;
import cl.almejo.vsim.core.logic.LogicValue;
import cl.almejo.vsim.core.logic.Minimization.Form;
import cl.almejo.vsim.core.logic.PracticeResult;
import cl.almejo.vsim.core.logic.Term;
import cl.almejo.vsim.core.logic.TruthTableSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The student's groups of the Karnaugh practice mode (spec §2), owned by the {@link KarnaughModel} (never saved): per
 * output and per form (a sum of products groups 1s, a product of sums 0s, so the two lists are separate) the groups in
 * drawing order, the selected group, and the hints. The verdicts and the grade come from core ({@link GroupCheck},
 * {@link PracticeResult}) and are cached until the groups or the table change.
 * <p>
 * Table changes (told by the model through {@link #tableChanged}): an output whose column changed (values or input
 * count) loses its groups in both forms, and its name goes to {@link #droppedOutputs()} when it had any; a removed
 * output loses them silently; titles and names keep them. The «selected» and hint accessors without arguments are
 * about the output and form the wizard shows ({@link KarnaughModel#selectedOutput()}, the step-4 form).
 */
public final class PracticeModel {

    /** The most groups one output and form can have (a 4×4 map has 16 cells). */
    public static final int MAXIMUM_GROUPS = 16;

    private record Key(int output, Form form) {
    }

    /** What the student did on one output and form. */
    private static final class Work {
        private final List<DrawnGroup> groups = new ArrayList<>();
        private int selected = -1;
        private int hintedRow = -1;
        private boolean hintGroupShown;
        private int hintsUsed;
        /** VSim's solution was shown («Revisar» or «Me rindo»); kept until the output's column changes. */
        private boolean revealed;
    }

    private final KarnaughModel model;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<Key, Work> works = new HashMap<>();
    private final Map<Key, List<GroupCheck.Result>> verdictCache = new HashMap<>();
    private final Map<Key, PracticeResult> resultCache = new HashMap<>();
    private final List<String> droppedOutputs = new ArrayList<>();
    /** The output columns when the groups were last checked against the table. */
    private List<List<LogicValue>> knownColumns = List.of();
    private boolean practising;

    PracticeModel(KarnaughModel model) {
        this.model = model;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /**
     * Whether the wizard is the practice one («Practicar Karnaugh…»): step 3 is the practice and step 4 offers
     * «Usar mis grupos». Fixed by the wizard when it is created ({@link KarnaughWizard.Mode}); false (the solution)
     * by default.
     */
    public boolean practising() {
        return practising;
    }

    void setPractising(boolean newPractising) {
        if (newPractising != practising) {
            practising = newPractising;
            fire();
        }
    }

    /** The groups of output {@code output} in {@code form}, in drawing order. */
    public List<DrawnGroup> groups(int output, Form form) {
        Work work = works.get(new Key(output, form));
        return work == null ? List.of() : List.copyOf(work.groups);
    }

    /** Whether the student drew at least one group on output {@code output} in {@code form}. */
    public boolean practised(int output, Form form) {
        Work work = works.get(new Key(output, form));
        return work != null && !work.groups.isEmpty();
    }

    /** Adds {@code group} after the others; refused (false) when the output already has {@link #MAXIMUM_GROUPS}. */
    public boolean add(int output, Form form, DrawnGroup group) {
        Work work = work(output, form);
        if (work.groups.size() >= MAXIMUM_GROUPS) {
            return false;
        }
        work.groups.add(group);
        edited(work);
        return true;
    }

    /** Removes the group at {@code index}; the selection follows the group it pointed at (cleared when removed). */
    public void remove(int output, Form form, int index) {
        Work work = work(output, form);
        if (index < 0 || index >= work.groups.size()) {
            return;
        }
        work.groups.remove(index);
        if (work.selected == index) {
            work.selected = -1;
        } else if (work.selected > index) {
            work.selected--;
        }
        edited(work);
    }

    /** «Borrar todos»: removes every group of output {@code output} in {@code form}. */
    public void clear(int output, Form form) {
        Work work = work(output, form);
        if (work.groups.isEmpty()) {
            return;
        }
        work.groups.clear();
        work.selected = -1;
        edited(work);
    }

    /** The selected group of the shown output and form. */
    public OptionalInt selected() {
        return selected(model.selectedOutput(), shownForm());
    }

    OptionalInt selected(int output, Form form) {
        Work work = works.get(new Key(output, form));
        return work == null || work.selected < 0 ? OptionalInt.empty() : OptionalInt.of(work.selected);
    }

    /** Selects group {@code index} of the shown output and form (out of range clears the selection). */
    public void select(int index) {
        Work work = work(model.selectedOutput(), shownForm());
        int newSelected = index >= 0 && index < work.groups.size() ? index : -1;
        if (newSelected != work.selected) {
            work.selected = newSelected;
            fire();
        }
    }

    public void clearSelection() {
        select(-1);
    }

    /** The verdict of each group of output {@code output} in {@code form}, each judged against the others. */
    public List<GroupCheck.Result> verdicts(int output, Form form) {
        Key key = new Key(output, form);
        List<GroupCheck.Result> cached = verdictCache.get(key);
        if (cached == null) {
            List<Set<GridCell>> cells = groups(output, form).stream().map(DrawnGroup::cells).toList();
            cached = GroupCheck.checkAll(spec(), output, form, cells);
            verdictCache.put(key, cached);
        }
        return cached;
    }

    /** The terms of the groups that are not red, in drawing order: what the student's cover is. */
    public List<Term> validTerms(int output, Form form) {
        List<Term> terms = new ArrayList<>();
        for (GroupCheck.Result result : verdicts(output, form)) {
            if (!result.verdict().isInvalid()) {
                terms.add(result.term().orElseThrow());
            }
        }
        return terms;
    }

    /** The grade of the student's cover (the terms of the groups that are not red). */
    public PracticeResult result(int output, Form form) {
        Key key = new Key(output, form);
        PracticeResult cached = resultCache.get(key);
        if (cached == null) {
            cached = PracticeResult.of(spec(), output, form, validTerms(output, form));
            resultCache.put(key, cached);
        }
        return cached;
    }

    /**
     * «Pista» on the shown output and form: the first press marks an uncovered cell (an essential one first); a
     * second press on the same cell shows a group that covers it. Any edit of the groups takes the hint away, so a
     * press after it marks a cell again. Each press that shows something counts; false when every cell the form needs
     * is covered (nothing to hint, not counted).
     */
    public boolean pressHint() {
        int output = model.selectedOutput();
        Form form = shownForm();
        OptionalInt row = PracticeResult.hintRow(spec(), output, form, validTerms(output, form));
        if (row.isEmpty()) {
            return false;
        }
        Work work = work(output, form);
        if (work.hintedRow == row.getAsInt() && !work.hintGroupShown) {
            work.hintGroupShown = true;
        } else if (work.hintedRow == row.getAsInt()) {
            return false;
        } else {
            work.hintedRow = row.getAsInt();
            work.hintGroupShown = false;
        }
        work.hintsUsed++;
        fire();
        return true;
    }

    /** The table row the hint marks on the shown output and form, if any. */
    public OptionalInt hintRow() {
        Work work = works.get(new Key(model.selectedOutput(), shownForm()));
        return work == null || work.hintedRow < 0 ? OptionalInt.empty() : OptionalInt.of(work.hintedRow);
    }

    /** The group the second hint press shows (the largest prime covering the hinted cell), if shown. */
    public Optional<Term> hintGroup() {
        Work work = works.get(new Key(model.selectedOutput(), shownForm()));
        if (work == null || work.hintedRow < 0 || !work.hintGroupShown) {
            return Optional.empty();
        }
        return Optional.of(PracticeResult.hintGroup(spec(), model.selectedOutput(), shownForm(), work.hintedRow));
    }

    /** The hints used on output {@code output} in {@code form} since its groups were last dropped. */
    public int hintsUsed(int output, Form form) {
        Work work = works.get(new Key(output, form));
        return work == null ? 0 : work.hintsUsed;
    }

    /**
     * Whether VSim's solution of output {@code output} in {@code form} was shown («Revisar» or «Me rindo»): from then
     * on step 4 may show VSim's expression for it. It stays revealed (whatever the groups do) until the output's column
     * changes, which makes it a new function.
     */
    public boolean revealed(int output, Form form) {
        Work work = works.get(new Key(output, form));
        return work != null && work.revealed;
    }

    /** VSim's solution of output {@code output} in {@code form} was shown. */
    public void reveal(int output, Form form) {
        Work work = work(output, form);
        if (!work.revealed) {
            work.revealed = true;
            fire();
        }
    }

    /** The names of the outputs whose groups a table change dropped, for «La tabla cambió…»; empty when none. */
    public List<String> droppedOutputs() {
        return List.copyOf(droppedOutputs);
    }

    /** Dismisses «La tabla cambió…» (the next edit of the groups dismisses it too). */
    public void clearDroppedNotice() {
        if (!droppedOutputs.isEmpty()) {
            droppedOutputs.clear();
            fire();
        }
    }

    /**
     * The table changed (anything: values, counts, names, title): drops the groups of every output whose column is
     * not the one they were drawn on, and of removed outputs. Called by the model before it tells its listeners, so
     * this fires nothing itself.
     */
    void tableChanged(List<List<LogicValue>> columns, List<String> outputNames) {
        verdictCache.clear();
        resultCache.clear();
        Iterator<Map.Entry<Key, Work>> iterator = works.entrySet().iterator();
        while (iterator.hasNext()) {
            int output = iterator.next().getKey().output();
            if (output >= columns.size()) {
                iterator.remove();
            }
        }
        for (int output = 0; output < columns.size() && output < knownColumns.size(); output++) {
            if (!columns.get(output).equals(knownColumns.get(output))) {
                drop(output, outputNames.get(output).strip());
            }
        }
        knownColumns = columns.stream().map(List::copyOf).toList();
    }

    private void drop(int output, String name) {
        boolean hadGroups = false;
        for (Form form : Form.values()) {
            Work work = works.remove(new Key(output, form));
            hadGroups |= work != null && !work.groups.isEmpty();
        }
        if (hadGroups && !droppedOutputs.contains(name)) {
            droppedOutputs.add(name);
        }
    }

    private void edited(Work work) {
        work.hintedRow = -1;
        work.hintGroupShown = false;
        droppedOutputs.clear();
        fire();
    }

    private Work work(int output, Form form) {
        return works.computeIfAbsent(new Key(output, form), key -> new Work());
    }

    private Form shownForm() {
        return model.options().form();
    }

    private TruthTableSpec spec() {
        return model.spec();
    }

    private void fire() {
        verdictCache.clear();
        resultCache.clear();
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
