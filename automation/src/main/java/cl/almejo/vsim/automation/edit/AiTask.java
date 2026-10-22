package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.TaskInfo;
import cl.almejo.vsim.core.document.Guide;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

/**
 * One AI task (spec §3): its title, its plan of stages and steps, whether it still runs, and the edits it applied,
 * so «Deshacer lo que hizo la IA» can take them back and its end can say what it changed. Thread-safe: the server's
 * threads begin, edit and end it while «Detener» stops it from the editor.
 */
public final class AiTask {

    /** Where a task is: running until it ends or the user stops it. */
    public enum State { RUNNING, ENDED, STOPPED }

    private final TaskInfo info;
    private List<TaskStage> stages;
    private State state = State.RUNNING;
    private final List<AiEditCommand> edits = new ArrayList<>();
    private final Set<String> changedElementIds = new LinkedHashSet<>();
    private final Set<Guide> layoutGuides = new LinkedHashSet<>();
    /** The stage the AI said it works on («begin stage»); the stage of a step that names none it knows. */
    private int currentStage;
    /** The elements each stage added and no later step removed (the storyboard frames them, spec §6). */
    private final Map<Integer, Set<String>> createdPerStage = new TreeMap<>();
    /**
     * The parts a component was made of without replacing them, kept as «cómo se hizo» so their frame is locked, with
     * the stage that made the component (the lock is a step of that stage).
     */
    private final Map<String, Integer> howItWasMade = new LinkedHashMap<>();
    /** The frame the storyboard put around each stage. */
    private final Map<Integer, String> stageFrames = new TreeMap<>();

    AiTask(TaskInfo info, List<TaskStage> stages) {
        this.info = Objects.requireNonNull(info, "info");
        this.stages = List.copyOf(stages);
    }

    public TaskInfo info() {
        return info;
    }

    public synchronized List<TaskStage> stages() {
        return stages;
    }

    public synchronized State state() {
        return state;
    }

    synchronized void replacePlan(List<TaskStage> newStages) {
        stages = List.copyOf(newStages);
    }

    /** Ends a running task; false, changing nothing, when it had ended or was stopped already. */
    synchronized boolean end() {
        return leave(State.ENDED);
    }

    /** Stops a running task («Detener»); false when it had ended or was stopped already. */
    synchronized boolean stop() {
        return leave(State.STOPPED);
    }

    private boolean leave(State next) {
        if (state != State.RUNNING) {
            return false;
        }
        state = next;
        return true;
    }

    /**
     * Records an applied edit, the elements it added, changed and removed, and the guides it added only to align
     * (taken away when the task ends, spec §7).
     */
    synchronized void recordEdit(AiEditCommand edit, EditReceipt.ElementChanges changes,
                                 List<Guide> addedLayoutGuides) {
        edits.add(edit);
        layoutGuides.addAll(addedLayoutGuides);
        changedElementIds.addAll(changes.added());
        changedElementIds.addAll(changes.changed());
        changes.removed().forEach(changedElementIds::remove);
        changes.removed().forEach(removed -> createdPerStage.values().forEach(created -> created.remove(removed)));
        if (edit.stageIndex() >= 0) {
            createdPerStage.computeIfAbsent(edit.stageIndex(), stage -> new LinkedHashSet<>())
                    .addAll(changes.added());
        }
    }

    /** The stage the AI works on now (0 until it begins another one). */
    public synchronized int currentStage() {
        return currentStage;
    }

    synchronized void enterStage(int stageIndex) {
        currentStage = stageIndex;
    }

    /**
     * The elements stage {@code stageIndex} added and no later step of the task removed; some may be gone since (an
     * undo, the user). Never anything the task did not add.
     */
    public synchronized Set<String> createdIn(int stageIndex) {
        return Set.copyOf(createdPerStage.getOrDefault(stageIndex, Set.of()));
    }

    /** The stages that added elements, in order. */
    public synchronized List<Integer> stagesThatCreated() {
        return createdPerStage.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
    }

    synchronized void recordHowItWasMade(Collection<String> elementIds, int stageIndex) {
        elementIds.forEach(elementId -> howItWasMade.merge(elementId, stageIndex, Math::max));
    }

    /** True when a component was made of some of {@code elementIds} without replacing them («cómo se hizo»). */
    public synchronized boolean madeAComponentOf(Collection<String> elementIds) {
        return stageThatMadeAComponentOf(elementIds).isPresent();
    }

    /** The latest stage that made a component of some of {@code elementIds} without replacing them. */
    public synchronized OptionalInt stageThatMadeAComponentOf(Collection<String> elementIds) {
        return elementIds.stream().filter(howItWasMade::containsKey).mapToInt(howItWasMade::get).max();
    }

    /** The frame the storyboard put around stage {@code stageIndex}, if it did (it may be gone since). */
    public synchronized Optional<String> frameOf(int stageIndex) {
        return Optional.ofNullable(stageFrames.get(stageIndex));
    }

    public synchronized void recordStageFrame(int stageIndex, String frameId) {
        stageFrames.put(stageIndex, frameId);
    }

    /** True while some edit of this task is still in the document (not undone). Editor thread only. */
    synchronized boolean hasAppliedEdits() {
        return edits.stream().anyMatch(AiEditCommand::isApplied);
    }

    /** True while some edit of stage {@code stageIndex} is still in the document. Editor thread only. */
    synchronized boolean hasAppliedEdits(int stageIndex) {
        return edits.stream().anyMatch(edit -> edit.isApplied() && edit.stageIndex() == stageIndex);
    }

    /**
     * The stage whose step «Deshacer etapa» takes back next: the latest stage with a step still in the document (its
     * frame counts; a stage that only checked has none). Editor thread only.
     */
    public synchronized OptionalInt latestStageWithAppliedEdits() {
        for (int index = edits.size() - 1; index >= 0; index--) {
            AiEditCommand edit = edits.get(index);
            if (edit.isApplied() && edit.stageIndex() >= 0) {
                return OptionalInt.of(edit.stageIndex());
            }
        }
        return OptionalInt.empty();
    }

    /** The guides the task added only to align, in order (some may be gone since). */
    synchronized List<Guide> layoutGuides() {
        return List.copyOf(layoutGuides);
    }

    /** The elements the task added or changed and that it did not remove afterwards. */
    synchronized Set<String> changedElementIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(changedElementIds));
    }
}
