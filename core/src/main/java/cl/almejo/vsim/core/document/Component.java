package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A placed component. {@code position} is the origin of the component's local coordinates; {@code labelOffset} is
 * where the user moved the label (empty = the automatic place).
 */
public record Component(ElementId id, String typeId, GridPoint position, Rotation rotation,
                        Map<String, Object> parameters, String label, Optional<LabelOffset> labelOffset)
        implements DocumentElement {

    /** A component whose label sits at the automatic place. */
    public Component(ElementId id, String typeId, GridPoint position, Rotation rotation,
                     Map<String, Object> parameters, String label) {
        this(id, typeId, position, rotation, parameters, label, Optional.empty());
    }

    public Component {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        label = label == null ? "" : label;
        labelOffset = labelOffset == null ? Optional.empty() : labelOffset;
    }

    public Component withPosition(GridPoint newPosition) {
        return new Component(id, typeId, newPosition, rotation, parameters, label, labelOffset);
    }

    public Component withRotation(Rotation newRotation) {
        return new Component(id, typeId, position, newRotation, parameters, label, labelOffset);
    }

    public Component withParameters(Map<String, Object> newParameters) {
        return new Component(id, typeId, position, rotation, newParameters, label, labelOffset);
    }

    public Component withLabel(String newLabel) {
        return new Component(id, typeId, position, rotation, parameters, newLabel, labelOffset);
    }

    public Component withLabelOffset(Optional<LabelOffset> newLabelOffset) {
        return new Component(id, typeId, position, rotation, parameters, label, newLabelOffset);
    }

    @Override
    public Component translate(int deltaX, int deltaY) {
        return withPosition(position.translate(deltaX, deltaY));
    }
}
