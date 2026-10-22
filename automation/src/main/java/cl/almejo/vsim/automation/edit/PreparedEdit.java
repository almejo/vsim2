package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.core.command.ReplaceContentCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An edit call planned against one version of the circuit and not written yet (IA.2, «Paso a paso»): what
 * {@link EditService#commit} writes, unchanged, when the circuit is still at {@link #version()}; so the step applied
 * after «Siguiente» is exactly the ghost that was shown (Review Focus 1). Made on the editor thread.
 */
public final class PreparedEdit {

    private final EditRequest request;
    private final Optional<AiTask> task;
    private final DocumentVersion version;
    private final EditPlanner.PlannedEdit planned;
    private final ReplaceContentCommand content;
    private final EditReceipt.ElementChanges changes;
    private final List<DocumentElement> ghostElements;
    private final Optional<Bounds> changedBounds;

    PreparedEdit(EditRequest request, Optional<AiTask> task, DocumentVersion version, EditPlanner.PlannedEdit planned,
                 ReplaceContentCommand content, EditReceipt.ElementChanges changes, CircuitDocument live,
                 ComponentTypeRegistry liveRegistry) {
        this.request = Objects.requireNonNull(request, "request");
        this.task = Objects.requireNonNull(task, "task");
        this.version = Objects.requireNonNull(version, "version");
        this.planned = Objects.requireNonNull(planned, "planned");
        this.content = Objects.requireNonNull(content, "content");
        this.changes = Objects.requireNonNull(changes, "changes");
        List<DocumentElement> ghosts = new ArrayList<>();
        for (ElementId id : content.addedIds()) {
            planned.candidate().element(id).ifPresent(ghosts::add);
        }
        for (ElementId id : content.changedIds()) {
            planned.candidate().element(id).ifPresent(ghosts::add);
        }
        this.ghostElements = List.copyOf(ghosts);
        List<DocumentElement> removed = new ArrayList<>();
        for (ElementId id : content.removedIds()) {
            live.element(id).ifPresent(removed::add);
        }
        this.changedBounds = union(bounds(ghostElements, planned.registry()), bounds(removed, liveRegistry));
    }

    public EditRequest request() {
        return request;
    }

    /** The version of the circuit it was planned against: {@link EditService#commit} writes it only there. */
    public DocumentVersion version() {
        return version;
    }

    /** What the receipt will say once written, with {@code applied} false: the aliases already name the final ids. */
    public EditReceipt proposal() {
        return EditService.receipt(false, version, planned, changes, List.of());
    }

    /** The elements it adds or changes, as they will be once written (the ghosts). */
    public List<DocumentElement> ghostElements() {
        return ghostElements;
    }

    /** The ids of the elements it removes. */
    public Set<ElementId> removedIds() {
        return content.removedIds();
    }

    /** The bounds of everything it adds, changes or removes; empty when it changes no element (only guides). */
    public Optional<Bounds> changedBounds() {
        return changedBounds;
    }

    /** True when writing it would change nothing. */
    public boolean changesNothing() {
        return content.changesNothing();
    }

    Optional<AiTask> task() {
        return task;
    }

    EditReceipt.ElementChanges changes() {
        return changes;
    }

    EditPlanner.PlannedEdit planned() {
        return planned;
    }

    ReplaceContentCommand content() {
        return content;
    }

    private static Optional<Bounds> bounds(List<DocumentElement> elements, ComponentTypeRegistry registry) {
        Bounds result = null;
        for (DocumentElement element : elements) {
            try {
                Bounds bounds = ElementBounds.of(element, registry);
                result = result == null ? bounds : result.union(bounds);
            } catch (RuntimeException unknownType) {
                // A part whose type cannot be resolved has no bounds to point at; the others still do.
            }
        }
        return Optional.ofNullable(result);
    }

    private static Optional<Bounds> union(Optional<Bounds> first, Optional<Bounds> second) {
        if (first.isEmpty()) {
            return second;
        }
        return second.map(other -> first.get().union(other)).or(() -> first);
    }
}
