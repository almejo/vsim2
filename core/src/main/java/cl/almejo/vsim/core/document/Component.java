package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Map;
import java.util.Objects;

/** A placed component. {@code position} is the origin of the component's local coordinates. */
public record Component(ElementId id, String typeId, GridPoint position, Rotation rotation,
                        Map<String, Object> parameters, String label) implements DocumentElement {

    public Component {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        label = label == null ? "" : label;
    }

    public Component withPosition(GridPoint newPosition) {
        return new Component(id, typeId, newPosition, rotation, parameters, label);
    }

    public Component withRotation(Rotation newRotation) {
        return new Component(id, typeId, position, newRotation, parameters, label);
    }

    public Component withParameters(Map<String, Object> newParameters) {
        return new Component(id, typeId, position, rotation, newParameters, label);
    }

    public Component withLabel(String newLabel) {
        return new Component(id, typeId, position, rotation, parameters, newLabel);
    }

    @Override
    public Component translate(int deltaX, int deltaY) {
        return withPosition(position.translate(deltaX, deltaY));
    }
}
