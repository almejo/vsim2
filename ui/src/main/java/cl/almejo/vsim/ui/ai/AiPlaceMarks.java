package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PlaceRef;
import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.ui.app.Delay;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.window.PlaceMarks;
import cl.almejo.vsim.ui.window.tabs.TabMark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Where the AI works now (fix round D, item 5n; mockup 2026-11-06 option A): the places its calls read, edit, run or
 * open, as marks the tabs show. During a task of a circuit, the place the task touched last works until the task ends
 * (a place it left turns grey); a call without a task (or on another circuit) works for {@link #BRIEF_MILLISECONDS}.
 * A mark that stopped working stays grey for {@link #FINISHED_MILLISECONDS}, or until the user brings its tab to the
 * front, and then goes. One for the whole application, fed by {@link EditorAutomationHost}'s place listener and the
 * server's task events. EDT only; the clock and the timer are injected (tests move them by hand).
 */
public final class AiPlaceMarks implements PlaceMarks {

    /** How long a call without a task keeps its place working: about the gap between two calls of a busy AI. */
    static final long BRIEF_MILLISECONDS = 3_000;
    /** How long a finished mark stays grey: «unos segundos» (mockup), enough to notice where the AI was. */
    static final long FINISHED_MILLISECONDS = 5_000;
    /** Still working: held by a running task, so it has no end yet. */
    private static final long HELD = Long.MAX_VALUE;

    /** One marked place: works until {@code workingUntil}, then is grey for {@link #FINISHED_MILLISECONDS}. */
    private static final class Mark {
        private final OpenDocument circuit;
        private final PlaceRef place;
        private Optional<String> taskId = Optional.empty();
        private Optional<String> taskTitle = Optional.empty();
        private long workingUntil;

        private Mark(OpenDocument circuit, PlaceRef place) {
            this.circuit = circuit;
            this.place = place;
        }

        private long goneAt() {
            return workingUntil == HELD ? HELD : workingUntil + FINISHED_MILLISECONDS;
        }

        private TabMark shown(long now) {
            return new TabMark(now < workingUntil ? TabMark.Phase.WORKING : TabMark.Phase.FINISHED, taskTitle);
        }

        private boolean isHeldBy(String task) {
            return workingUntil == HELD && taskId.filter(task::equals).isPresent();
        }
    }

    private final LongSupplier clock;
    private final Delay delay;
    private final List<Mark> marks = new ArrayList<>();
    /** The running task of each circuit, by the circuit's id (the task's document). */
    private final Map<DocumentId, TaskInfo> runningTasks = new HashMap<>();
    private final Listeners<Runnable> listeners = new Listeners<>();
    private Runnable pendingExpiry = () -> {
    };

    /** With the system clock and a Swing timer. */
    public AiPlaceMarks() {
        this(() -> System.nanoTime() / 1_000_000, Delay.swing());
    }

    AiPlaceMarks(LongSupplier clock, Delay delay) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.delay = Objects.requireNonNull(delay, "delay");
    }

    /** An AI call read, edited, ran or opened {@code place} of {@code circuit} (whose id is {@code circuitId}). */
    public void touched(DocumentId circuitId, OpenDocument circuit, PlaceRef place) {
        long now = clock.getAsLong();
        Optional<TaskInfo> task = Optional.ofNullable(runningTasks.get(circuitId.base()));
        Mark mark = markAt(circuit, place);
        if (task.isPresent()) {
            String taskId = task.get().taskId();
            marks.stream().filter(other -> other != mark && other.isHeldBy(taskId))
                    .forEach(left -> left.workingUntil = now);
            mark.taskId = Optional.of(taskId);
            mark.taskTitle = Optional.of(task.get().title());
            mark.workingUntil = HELD;
        } else if (mark.workingUntil != HELD) {
            mark.taskId = Optional.empty();
            mark.taskTitle = Optional.empty();
            mark.workingUntil = now + BRIEF_MILLISECONDS;
        }
        changed();
    }

    /** A task of the AI began: its circuit's places it touches work until it ends. */
    public void taskStarted(TaskInfo task) {
        runningTasks.put(task.document().base(), task);
    }

    /** The task ended or was stopped: the places it held turn grey. */
    public void taskEnded(TaskInfo task) {
        runningTasks.remove(task.document().base(), task);
        long now = clock.getAsLong();
        List<Mark> held = marks.stream().filter(mark -> mark.isHeldBy(task.taskId())).toList();
        held.forEach(mark -> mark.workingUntil = now);
        if (!held.isEmpty()) {
            changed();
        }
    }

    @Override
    public Optional<TabMark> markOf(OpenDocument document, Optional<String> definitionId,
                                    Optional<String> contextPath) {
        long now = clock.getAsLong();
        return TabMark.strongest(marks.stream()
                .filter(mark -> mark.goneAt() > now && shows(mark, document, definitionId, contextPath))
                .map(mark -> Optional.of(mark.shown(now))));
    }

    @Override
    public void visited(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath) {
        long now = clock.getAsLong();
        if (marks.removeIf(mark -> mark.workingUntil <= now && shows(mark, document, definitionId, contextPath))) {
            changed();
        }
    }

    @Override
    public Runnable addListener(Runnable listener) {
        return listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Whether a tab showing that place shows {@code mark}: the circuit's own tab for the main place; for a component,
     * its tabs on the same piece's values, or every tab of it when the AI gave no piece (its body is the same in all).
     */
    private static boolean shows(Mark mark, OpenDocument document, Optional<String> definitionId,
                                 Optional<String> contextPath) {
        if (mark.circuit != document) {
            return false;
        }
        if (mark.place.isMain()) {
            return definitionId.isEmpty();
        }
        return definitionId.filter(mark.place.definition()::equals).isPresent()
                && (mark.place.context().isEmpty() || mark.place.context().equals(contextPath));
    }

    private Mark markAt(OpenDocument circuit, PlaceRef place) {
        for (Mark mark : marks) {
            if (mark.circuit == circuit && mark.place.equals(place)) {
                return mark;
            }
        }
        Mark created = new Mark(circuit, place);
        marks.add(created);
        return created;
    }

    /** Drops the marks that went, tells the windows and waits for the next moment a mark greys or goes. */
    private void changed() {
        long now = clock.getAsLong();
        marks.removeIf(mark -> mark.goneAt() <= now);
        listeners.fire(Runnable::run);
        pendingExpiry.run();
        pendingExpiry = () -> {
        };
        long next = HELD;
        for (Mark mark : marks) {
            if (mark.workingUntil > now && mark.workingUntil != HELD) {
                next = Math.min(next, mark.workingUntil);
            }
            next = Math.min(next, mark.goneAt());
        }
        if (next != HELD) {
            pendingExpiry = delay.after((int) Math.max(1, next - now), this::changed);
        }
    }
}
