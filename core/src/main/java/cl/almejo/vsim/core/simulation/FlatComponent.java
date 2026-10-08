package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;

import java.util.Map;

public record FlatComponent(String id, ComponentType type, Map<String, Object> parameters) {
    public FlatComponent {
        parameters = Map.copyOf(parameters);
    }
}
