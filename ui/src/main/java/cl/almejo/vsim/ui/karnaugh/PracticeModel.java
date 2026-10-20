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
 * Each map (one output in one form) is {@link MapState#PENDING pending}, {@link MapState#DONE done} (the groups that
 * are not red cover the table, minimal or not) or {@link MapState#GIVEN_UP given up} («Me rindo», or «Seguir igual»
 * when leaving step 3): giving up is for good, the map's groups stay but can no longer change, and steps 4 and 5 use
 * VSim's terms for it.
 * <p>
 * Table changes (told by the model through {@link #tableChanged}): any change of the values, the input count or the
 * output count restarts the whole practice (every group, give-up, hint and reveal of every output and form), and
 * {@link #restarted()} says so until the next drawing; titles and names keep everything. The «selected» and hint
 * accessors without arguments are about the output and form the wizard shows ({@link KarnaughModel#selectedOutput()},
 * the step-4 form).
 */
public final class PracticeModel {

    /** The most groups one output and form can have (a 4×4 map has 16 cells). */
    public static final int MAXIMUM_GROUPS = 16;

    /** Where one map (one output in one form) stands. */
    public enum MapState {
        /** Something still to group. */
        PENDING,
        /** The student's groups that are not red cover the table (minimal or not). */
        DONE,
        /** The student gave up on it: read-only, VSim's solution shows. */
        GIVEN_UP
    }

    private record Key(int output, Form form) {
    }

    /** What the student did on one output and form. */
    private static final class Work {
        private final List<DrawnGroup> groups = new ArrayList<>();
        private int selected = -1;
        private int hintedRow = -1;
        private boolean hintGroupShown;
        private int hintsUsed;
        /** VSim's solution was shown («Revisar» or «Me rindo»); kept until the table changes. */
        private boolean revealed;
        /** The student gave up on this map, for good (until the table changes). */
        private boolean givenUp;

        /** Nothing done here that a restart would lose. */
        boolean blank() {
            return groups.isEmpty() && !revealed && !givenUp && hintsUsed == 0 && hintedRow < 0;
        }
    }

    private final KarnaughModel model;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<Key, Work> works = new HashMap<>();
    private final Map<Key, List<GroupCheck.Result>> verdictCache = new HashMap<>();
    private final Map<Key, PracticeResult> resultCache = new HashMap<>();
    /** «La tabla cambió: la práctica empezó de nuevo» shows until the next drawing. */
    private boolean restarted;
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

    /**
     * Adds {@code group} after the others; refused (false) when the output already has {@link #MAXIMUM_GROUPS} or the
     * student gave up on it.
     */
    public boolean add(int output, Form form, DrawnGroup group) {
        Work work = work(output, form);
        if (work.givenUp || work.groups.size() >= MAXIMUM_GROUPS) {
            return false;
        }
        work.groups.add(group);
        edited(work);
        return true;
    }

    /** Removes the group at {@code index}; the selection follows the group it pointed at (cleared when removed). */
    public void remove(int output, Form form, int index) {
        Work work = work(output, form);
        if (work.givenUp || index < 0 || index >= work.groups.size()) {
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
        if (work.givenUp || work.groups.isEmpty()) {
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

    /**
     * Selects group {@code index} of the shown output and form (out of range, or on a map given up, clears the
     * selection).
     */
    public void select(int index) {
        Work work = work(model.selectedOutput(), shownForm());
        int newSelected = !work.givenUp && index >= 0 && index < work.groups.size() ? index : -1;
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
     * is covered or the map was given up (nothing to hint, not counted).
     */
    public boolean pressHint() {
        int output = model.selectedOutput();
        Form form = shownForm();
        if (givenUp(output, form)) {
            return false;
        }
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
     * on step 4 may show VSim's expression for it. It stays revealed (whatever the groups do) until the table changes,
     * which restarts the practice.
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

    /** Whether the student gave up on output {@code output} in {@code form} (for good, until the table changes). */
    public boolean givenUp(int output, Form form) {
        Work work = works.get(new Key(output, form));
        return work != null && work.givenUp;
    }

    /**
     * Gives up on output {@code output} in {@code form}, for good: VSim's solution is revealed, the map keeps the
     * student's groups but takes no more edits, selections or hints, and steps 4 and 5 use VSim's terms for it.
     */
    public void giveUp(int output, Form form) {
        Work work = work(output, form);
        if (work.givenUp) {
            return;
        }
        work.givenUp = true;
        work.revealed = true;
        work.selected = -1;
        work.hintedRow = -1;
        work.hintGroupShown = false;
        fire();
    }

    /** Where map {@code output} in {@code form} stands: given up, done (its cover is equivalent) or pending. */
    public MapState state(int output, Form form) {
        if (givenUp(output, form)) {
            return MapState.GIVEN_UP;
        }
        return result(output, form).equivalent() ? MapState.DONE : MapState.PENDING;
    }

    /** The outputs whose map in the chosen form is still pending, in table order. */
    public List<Integer> pendingOutputs() {
        List<Integer> pending = new ArrayList<>();
        for (int output = 0; output < model.outputCount(); output++) {
            if (state(output, shownForm()) == MapState.PENDING) {
                pending.add(output);
            }
        }
        return pending;
    }

    /** Whether a table change restarted the practice and nothing was drawn since («La tabla cambió…»). */
    public boolean restarted() {
        return restarted;
    }

    /** Dismisses «La tabla cambió…» (the next drawing dismisses it too). */
    public void clearRestartedNotice() {
        if (restarted) {
            restarted = false;
            fire();
        }
    }

    /**
     * The table changed (anything: values, counts, names, title): when the output columns are not the ones the
     * practice was done on (a value, the input count or the output count changed) the whole practice restarts, with
     * the notice when it loses something. Called by the model before it tells its listeners, so this fires nothing.
     */
    void tableChanged(List<List<LogicValue>> columns) {
        verdictCache.clear();
        resultCache.clear();
        if (!columns.equals(knownColumns)) {
            boolean lost = works.values().stream().anyMatch(work -> !work.blank());
            works.clear();
            restarted |= lost;
        }
        knownColumns = columns.stream().map(List::copyOf).toList();
    }

    private void edited(Work work) {
        work.hintedRow = -1;
        work.hintGroupShown = false;
        restarted = false;
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
