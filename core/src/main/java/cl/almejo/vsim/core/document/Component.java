package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A placed component. {@code position} is the origin of the component's local coordinates; {@code labelOffset} is
 * where the user moved the label (empty = the automatic place). {@code memoryContents} is a user-component piece's
 * own initial contents of memories inside it (spec §6.6b), by the memory's path inside the piece ({@code "c10"},
 * {@code "c2/c10"} for a deeper one), each as {@code MemoryContents.encode()} gives it; empty for every other part
 * and for a piece that uses its component's contents.
 */
public record Component(ElementId id, String typeId, GridPoint position, Rotation rotation,
                        Map<String, Object> parameters, String label, Optional<LabelOffset> labelOffset,
                        Optional<ComponentAppearance> appearance, Map<String, String> memoryContents)
        implements DocumentElement {

    /** A component whose label sits at the automatic place. */
    public Component(ElementId id, String typeId, GridPoint position, Rotation rotation,
                     Map<String, Object> parameters, String label) {
        this(id, typeId, position, rotation, parameters, label, Optional.empty());
    }

    /** A placed component without its own appearance. */
    public Component(ElementId id, String typeId, GridPoint position, Rotation rotation,
                     Map<String, Object> parameters, String label, Optional<LabelOffset> labelOffset) {
        this(id, typeId, position, rotation, parameters, label, labelOffset, Optional.empty());
    }

    /** A placed component whose memories (if it is a piece with any) use its component's contents. */
    public Component(ElementId id, String typeId, GridPoint position, Rotation rotation,
                     Map<String, Object> parameters, String label, Optional<LabelOffset> labelOffset,
                     Optional<ComponentAppearance> appearance) {
        this(id, typeId, position, rotation, parameters, label, labelOffset, appearance, Map.of());
    }

    public Component {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        label = label == null ? "" : label;
        labelOffset = labelOffset == null ? Optional.empty() : labelOffset;
        Objects.requireNonNull(appearance, "appearance");
        memoryContents = memoryContents == null ? Map.of() : Map.copyOf(memoryContents);
    }

    public Component withPosition(GridPoint newPosition) {
        return new Component(id, typeId, newPosition, rotation, parameters, label, labelOffset,
                appearance, memoryContents);
    }

    public Component withRotation(Rotation newRotation) {
        return new Component(id, typeId, position, newRotation, parameters, label, labelOffset,
                appearance, memoryContents);
    }

    public Component withParameters(Map<String, Object> newParameters) {
        return new Component(id, typeId, position, rotation, newParameters, label, labelOffset,
                appearance, memoryContents);
    }

    public Component withLabel(String newLabel) {
        return new Component(id, typeId, position, rotation, parameters, newLabel, labelOffset,
                appearance, memoryContents);
    }

    public Component withLabelOffset(Optional<LabelOffset> newLabelOffset) {
        return new Component(id, typeId, position, rotation, parameters, label, newLabelOffset,
                appearance, memoryContents);
    }

    public Component withAppearance(Optional<ComponentAppearance> newAppearance) {
        return new Component(id, typeId, position, rotation, parameters, label, labelOffset,
                newAppearance, memoryContents);
    }

    /** This piece with {@code newMemoryContents} as its own memory contents (see the record's description). */
    public Component withMemoryContents(Map<String, String> newMemoryContents) {
        return new Component(id, typeId, position, rotation, parameters, label, labelOffset, appearance,
                newMemoryContents);
    }

    @Override
    public Component translate(int deltaX, int deltaY) {
        return withPosition(position.translate(deltaX, deltaY));
    }
}
