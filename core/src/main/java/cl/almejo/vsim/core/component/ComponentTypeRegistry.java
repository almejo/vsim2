package cl.almejo.vsim.core.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ComponentTypeRegistry {

    private final Map<String, ComponentType> types = new LinkedHashMap<>();

    public void register(ComponentType type) {
        if (types.containsKey(type.id())) {
            throw new IllegalArgumentException("component type already registered: " + type.id());
        }
        types.put(type.id(), type);
    }

    public Optional<ComponentType> find(String id) {
        return Optional.ofNullable(types.get(id));
    }

    public ComponentType require(String id) {
        ComponentType type = types.get(id);
        if (type == null) {
            throw new UnknownComponentTypeException(id);
        }
        return type;
    }

    public List<ComponentType> all() {
        return List.copyOf(types.values());
    }
}
