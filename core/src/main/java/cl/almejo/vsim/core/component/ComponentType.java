package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Describes a kind of component: geometry, pins, parameters and behavior. Drawing lives in the UI. */
public record ComponentType(String id, ComponentCategory category, GridSize size, List<PinSpec> pins,
                            List<ParameterSpec> parameters, BehaviorFactory behaviorFactory) {

    public ComponentType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(behaviorFactory, "behaviorFactory");
        pins = List.copyOf(pins);
        parameters = List.copyOf(parameters);
    }

    public int pinCount() {
        return pins.size();
    }

    public PinSpec pin(int index) {
        return pins.get(index);
    }

    public Map<String, Object> resolveParameters(Map<String, Object> raw) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (ParameterSpec parameter : parameters) {
            Object rawValue = raw == null ? null : raw.get(parameter.name());
            resolved.put(parameter.name(), rawValue == null ? parameter.defaultValue() : parameter.normalize(rawValue));
        }
        return Map.copyOf(resolved);
    }

    /** Creates a document component of this type, normalizing {@code rawParameters} (invalid values throw). */
    public Component newComponent(ElementId id, GridPoint position, Rotation rotation,
                                  Map<String, Object> rawParameters, String label) {
        return new Component(id, this.id, position, rotation, resolveParameters(rawParameters), label);
    }

    public GridPoint pinLocation(Component component, int pinIndex) {
        return component.position().plus(component.rotation().apply(pins.get(pinIndex).position()));
    }

    public Bounds bounds(Component component) {
        return Bounds.rotated(component.position(), size, component.rotation());
    }

    public Behavior createBehavior(Map<String, Object> rawParameters) {
        return behaviorFactory.create(resolveParameters(rawParameters));
    }
}
