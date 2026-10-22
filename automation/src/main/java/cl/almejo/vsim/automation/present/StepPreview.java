package cl.almejo.vsim.automation.present;

import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One step as «Presentar» shows it (spec §5): where it belongs, its text and reason, the elements it adds or moves
 * (as they will be once applied, painted as ghosts) and the ids it removes (painted dimmed), and the bounds the
 * callout points at.
 */
public record StepPreview(String taskId, int stageIndex, int stepIndex, String text, Optional<String> reason,
                          List<DocumentElement> ghostElements, Set<ElementId> removedIds,
                          Optional<Bounds> changedBounds) {

    public StepPreview {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(changedBounds, "changedBounds");
        ghostElements = List.copyOf(ghostElements);
        removedIds = Set.copyOf(removedIds);
    }
}
