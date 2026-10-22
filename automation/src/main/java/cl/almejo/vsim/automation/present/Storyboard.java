package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.PresentationHost;
import cl.almejo.vsim.automation.edit.AiTask;
import cl.almejo.vsim.automation.edit.EditService;
import cl.almejo.vsim.automation.edit.EditableCircuit;
import cl.almejo.vsim.automation.edit.TaskStage;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * «Presentar»'s storyboard (IA.2, spec §6): every stage of a task lives in its own titled frame, to the right of the
 * previous one, so the finished circuit reads like a storyboard.
 * <ul>
 *     <li>{@link #beginStage}: the free origin to the right of the previous stage's frame ({@link #GAP} units between
 *     frames, aligned on its top) where the AI builds the stage, and the view travels there;</li>
 *     <li>when a later stage begins (or its first step arrives) and when the task ends, each stage before it is
 *     wrapped in a frame titled with the stage — one more undo step of that stage, locked when a component was made
 *     of its parts without replacing them («cómo se hizo»). The frame holds only what the stage added: when a
 *     rectangle around it would take in anything else (the user's own parts, another stage's), it is drawn tighter,
 *     and when no rectangle can avoid them the stage stays without a frame;</li>
 *     <li>{@link #keepOnlyResult}: removes the other stages' frames and what they added, keeping the last stage and
 *     every component definition, as one undo step.</li>
 * </ul>
 * Frames are made only in «Presentar»; in working mode the AI frames what it wants itself.
 */
public final class Storyboard implements PresentationService.StageBoundary {

    /** The space between two stage frames. */
    public static final int GAP = 6;
    /** The room around a stage's parts inside its frame (as {@code MakeFrame} leaves it). */
    static final int FRAME_MARGIN = 2;
    /** The extra room above the parts for the frame's title. */
    static final int TITLE_ROOM = 2;
    /** The area a new stage is expected to take: the origin is chosen so that nothing lies there. */
    static final int STAGE_WIDTH = 30;
    static final int STAGE_HEIGHT = 20;

    private final PresentationHost host;
    private final EditService edits;
    private final Predicate<String> stepPending;

    /** @param stepPending whether a step of a task waits for «Siguiente» (asked on the editor thread) */
    public Storyboard(PresentationHost host, EditService edits, Predicate<String> stepPending) {
        this.host = Objects.requireNonNull(host, "host");
        this.edits = Objects.requireNonNull(edits, "edits");
        this.stepPending = Objects.requireNonNull(stepPending, "stepPending");
    }

    /**
     * The AI begins stage {@code stageIndex} (from 0) of the task: in «Presentar» the stages before it get their
     * frames and the view travels to the returned origin; in working mode only the origin is returned. Refused while
     * a step waits for «Siguiente» ({@code NOT_ALLOWED}) and for a stage the plan does not have
     * ({@code INVALID_PARAMETER}). Off the editor thread.
     */
    public StageOrigin beginStage(String taskId, int stageIndex) {
        AiTask task = edits.requireTask(taskId);
        DocumentId document = task.info().document();
        GridPoint origin = edits.onCircuit(document, circuit -> {
            requireNoPendingStep(taskId);
            edits.enterStage(taskId, stageIndex);
            boolean presenting = host.presentationSettings(document).presenting();
            if (presenting) {
                frameStagesBefore(circuit, task, stageIndex);
            }
            GridPoint free = originOf(circuit.document(), circuit.registry(), task, stageIndex);
            if (presenting) {
                host.travelToStageOrigin(document, free);
            }
            return free;
        });
        return new StageOrigin(origin.x(), origin.y());
    }

    /** Editor thread, «Presentar»: a step of stage {@code stageIndex} arrives; a later stage begins with it. */
    @Override
    public void reached(EditableCircuit circuit, AiTask task, int stageIndex) {
        if (stageIndex > task.currentStage()) {
            edits.enterStage(task.info().taskId(), stageIndex);
            frameStagesBefore(circuit, task, stageIndex);
        }
    }

    /** The running task is about to end: in «Presentar» its stages without a frame get theirs. Off the editor thread. */
    public void taskEnding(String taskId) {
        Optional<AiTask> found = edits.task(taskId);
        if (found.isEmpty() || found.get().state() != AiTask.State.RUNNING) {
            return;
        }
        AiTask task = found.get();
        DocumentId document = task.info().document();
        try {
            edits.onCircuit(document, circuit -> {
                if (host.presentationSettings(document).presenting()) {
                    frameStagesBefore(circuit, task, Integer.MAX_VALUE);
                }
                return Boolean.TRUE;
            });
        } catch (AutomationException closed) {
            if (closed.error() != AutomationError.DOCUMENT_CLOSED) {
                throw closed;
            }
        }
    }

    /**
     * «Dejar solo el resultado» (spec §6): removes the frames of the task's stages but the last one and what those
     * stages added, as one undo step; the last stage, everything inside its frame, the component definitions and
     * anything the task did not add stay. Refused while a step waits for «Siguiente» or the task still runs
     * ({@code NOT_ALLOWED}); with a single stage it changes nothing. Returns how many elements went away.
     */
    public int keepOnlyResult(String taskId) {
        AiTask task = edits.requireTask(taskId);
        return edits.onCircuit(task.info().document(), circuit -> {
            requireNoPendingStep(taskId);
            if (task.state() == AiTask.State.RUNNING) {
                throw new AutomationException(AutomationError.NOT_ALLOWED, "«Dejar solo el resultado» comes when "
                        + "the task " + taskId + " has ended", Map.of("taskId", taskId));
            }
            return keepOnlyResult(circuit, task);
        });
    }

    private int keepOnlyResult(EditableCircuit circuit, AiTask task) {
        CircuitDocument candidate = circuit.document().copy();
        List<Integer> stages = task.stagesThatCreated().stream()
                .filter(stage -> !live(candidate, task.createdIn(stage)).isEmpty())
                .toList();
        if (stages.size() < 2) {
            return 0;
        }
        int result = stages.getLast();
        Set<ElementId> kept = new HashSet<>(ids(live(candidate, task.createdIn(result))));
        liveFrame(candidate, task, result).ifPresent(frame ->
                kept.addAll(FrameContents.of(candidate, circuit.registry(), frame)));
        int removed = 0;
        for (int stage : stages.subList(0, stages.size() - 1)) {
            for (DocumentElement element : live(candidate, task.createdIn(stage))) {
                if (!kept.contains(element.id()) && candidate.remove(element.id()).isPresent()) {
                    removed++;
                }
            }
        }
        edits.writeTaskStep(circuit, task, EditService.NO_STAGE, candidate,
                EditService.KEEP_ONLY_RESULT_DESCRIPTION, false);
        return removed;
    }

    // --- frames ---

    /**
     * Editor thread: each stage before {@code limit} that added something gets its frame, one undo step of that
     * stage each; a frame made earlier is locked once a component was made of its stage's parts.
     */
    private void frameStagesBefore(EditableCircuit circuit, AiTask task, int limit) {
        for (int stage : task.stagesThatCreated()) {
            if (stage >= limit) {
                continue;
            }
            CircuitDocument candidate = circuit.document().copy();
            Optional<Frame> existing = liveFrame(candidate, task, stage);
            boolean locked = task.madeAComponentOf(task.createdIn(stage));
            if (existing.isPresent()) {
                if (locked && !existing.get().locked()) {
                    candidate.replace(existing.get().withLocked(true));
                    edits.writeTaskStep(circuit, task, stage, candidate, EditService.STAGE_FRAME_DESCRIPTION, true);
                }
                continue;
            }
            Optional<Frame> frame = frameFor(candidate, circuit.registry(), task, stage, locked);
            if (frame.isPresent()) {
                candidate.add(frame.get());
                edits.writeTaskStep(circuit, task, stage, candidate, EditService.STAGE_FRAME_DESCRIPTION, true);
                task.recordStageFrame(stage, frame.get().id().value());
            }
        }
    }

    /**
     * A frame titled with the stage around what it added, as roomy as possible without taking in anything else;
     * empty when even the tightest one would, or when the stage has nothing left.
     */
    static Optional<Frame> frameFor(CircuitDocument candidate, ComponentTypeRegistry registry, AiTask task,
                                    int stage, boolean locked) {
        List<DocumentElement> elements = live(candidate, task.createdIn(stage));
        Optional<Bounds> around = boundsOf(elements, registry);
        if (around.isEmpty()) {
            return Optional.empty();
        }
        Set<ElementId> own = ids(elements);
        String title = titleOf(task.stages(), stage);
        ElementId id = candidate.newFrameId();
        for (int margin = FRAME_MARGIN; margin >= 0; margin--) {
            for (int titleRoom = TITLE_ROOM; titleRoom >= 0; titleRoom--) {
                Frame frame = around(id, around.get(), margin, titleRoom, title, locked);
                if (own.containsAll(FrameContents.of(candidate, registry, frame))) {
                    return Optional.of(frame);
                }
            }
        }
        return Optional.empty();
    }

    private static Frame around(ElementId id, Bounds bounds, int margin, int titleRoom, String title,
                                boolean locked) {
        int top = margin + titleRoom;
        return new Frame(id, new GridPoint(bounds.x() - margin, bounds.y() - top),
                Math.max(Frame.MINIMUM_WIDTH, bounds.width() + 2 * margin),
                Math.max(Frame.MINIMUM_HEIGHT, bounds.height() + margin + top), title, FrameTint.NONE, locked);
    }

    /** The frame the storyboard put around {@code stage}, while it is in the circuit. */
    private static Optional<Frame> liveFrame(CircuitDocument document, AiTask task, int stage) {
        return task.frameOf(stage).flatMap(id -> document.frame(new ElementId(id)));
    }

    // --- origins ---

    /**
     * Where stage {@code stageIndex} is built: right of the previous stage's frame (or of what it added, or of the
     * whole circuit for the first stage), {@link #GAP} units away and aligned on its top, moved further right until
     * nothing but the stage's own work lies in a {@link #STAGE_WIDTH} by {@link #STAGE_HEIGHT} area there.
     */
    static GridPoint originOf(CircuitDocument document, ComponentTypeRegistry registry, AiTask task,
                              int stageIndex) {
        Set<ElementId> ownWork = new HashSet<>();
        for (int stage : task.stagesThatCreated()) {
            if (stage >= stageIndex) {
                ownWork.addAll(ids(live(document, task.createdIn(stage))));
            }
        }
        List<Bounds> obstacles = new ArrayList<>();
        for (DocumentElement element : document.elements()) {
            if (!ownWork.contains(element.id())) {
                boundsOf(List.of(element), registry).ifPresent(obstacles::add);
            }
        }
        Optional<Bounds> previous = previousStageArea(document, registry, task, stageIndex)
                .or(() -> obstacles.stream().reduce(Bounds::union));
        int left = previous.map(area -> area.maximumX() + GAP).orElse(0);
        int top = previous.map(Bounds::y).orElse(0);
        for (int attempt = 0; attempt <= obstacles.size(); attempt++) {
            Bounds wanted = new Bounds(left, top, STAGE_WIDTH, STAGE_HEIGHT);
            OptionalInt blocking = obstacles.stream().filter(wanted::intersects).mapToInt(Bounds::maximumX).max();
            if (blocking.isEmpty()) {
                break;
            }
            left = blocking.getAsInt() + GAP;
        }
        return new GridPoint(left + FRAME_MARGIN, top + FRAME_MARGIN + TITLE_ROOM);
    }

    /** The frame of the closest earlier stage that has one, or the frame its parts would get. */
    private static Optional<Bounds> previousStageArea(CircuitDocument document, ComponentTypeRegistry registry,
                                                     AiTask task, int stageIndex) {
        for (int stage = stageIndex - 1; stage >= 0; stage--) {
            Optional<Frame> frame = liveFrame(document, task, stage);
            if (frame.isPresent()) {
                return frame.flatMap(found -> boundsOf(List.of(found), registry));
            }
            Optional<Bounds> parts = boundsOf(live(document, task.createdIn(stage)), registry);
            if (parts.isPresent()) {
                Bounds bounds = parts.get();
                int top = FRAME_MARGIN + TITLE_ROOM;
                return Optional.of(new Bounds(bounds.x() - FRAME_MARGIN, bounds.y() - top,
                        bounds.width() + 2 * FRAME_MARGIN, bounds.height() + FRAME_MARGIN + top));
            }
        }
        return Optional.empty();
    }

    // --- helpers ---

    private void requireNoPendingStep(String taskId) {
        if (stepPending.test(taskId)) {
            throw new AutomationException(AutomationError.NOT_ALLOWED, "A step of task " + taskId + " still waits "
                    + "for «Siguiente»: call vsim_await_step until it is applied, stale or stopped. Nothing was changed",
                    Map.of("taskId", taskId));
        }
    }

    /** The elements of {@code ids} still in {@code document}, in its order. */
    private static List<DocumentElement> live(CircuitDocument document, Set<String> ids) {
        return document.elements().stream().filter(element -> ids.contains(element.id().value())).toList();
    }

    private static Set<ElementId> ids(List<DocumentElement> elements) {
        Set<ElementId> ids = new LinkedHashSet<>();
        elements.forEach(element -> ids.add(element.id()));
        return ids;
    }

    /** The union of the elements' bounds; a part whose type is unknown has none. */
    private static Optional<Bounds> boundsOf(List<DocumentElement> elements, ComponentTypeRegistry registry) {
        Bounds result = null;
        for (DocumentElement element : elements) {
            try {
                Bounds bounds = ElementBounds.of(element, registry);
                result = result == null ? bounds : result.union(bounds);
            } catch (RuntimeException unknownType) {
                // No size to frame or to keep clear of.
            }
        }
        return Optional.ofNullable(result);
    }

    /** The stage's title for its frame; none for a stage the plan does not list. */
    private static String titleOf(List<TaskStage> stages, int stage) {
        return stage >= 0 && stage < stages.size() ? stages.get(stage).title() : "";
    }
}
