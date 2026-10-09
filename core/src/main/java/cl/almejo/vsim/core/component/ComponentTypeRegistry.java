package cl.almejo.vsim.core.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Component types by id. Registered types are listed by {@link #all()}. Resolvers add types computed on demand (the
 * {@code subcircuit:<id>} types of one document): {@link #find} and {@link #require} see them, {@link #all()} does not.
 * Thread-safe: the library watcher works on its own thread.
 */
public final class ComponentTypeRegistry {

    /** Supplies a type that is not registered, or empty. */
    @FunctionalInterface
    public interface TypeResolver {
        Optional<ComponentType> resolve(String typeId);
    }

    private final Map<String, ComponentType> types = new LinkedHashMap<>();
    private final List<TypeResolver> resolvers = new CopyOnWriteArrayList<>();

    public synchronized void register(ComponentType type) {
        if (types.containsKey(type.id())) {
            throw new IllegalArgumentException("component type already registered: " + type.id());
        }
        types.put(type.id(), type);
    }

    public Optional<ComponentType> find(String id) {
        ComponentType registered;
        synchronized (this) {
            registered = types.get(id);
        }
        if (registered != null) {
            return Optional.of(registered);
        }
        for (TypeResolver resolver : resolvers) {
            Optional<ComponentType> resolved = resolver.resolve(id);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    public ComponentType require(String id) {
        return find(id).orElseThrow(() -> new UnknownComponentTypeException(id));
    }

    /** Registered types in registration order; resolved types are never listed. */
    public synchronized List<ComponentType> all() {
        return List.copyOf(types.values());
    }

    public void addResolver(TypeResolver resolver) {
        resolvers.add(resolver);
    }

    /** A new registry with the same registered types and no resolvers. */
    public synchronized ComponentTypeRegistry copy() {
        ComponentTypeRegistry copy = new ComponentTypeRegistry();
        copy.types.putAll(types);
        return copy;
    }

    /** A new registry with the same registered types and resolvers, plus {@code resolver} consulted last. */
    public synchronized ComponentTypeRegistry extendedWith(TypeResolver resolver) {
        ComponentTypeRegistry extended = copy();
        extended.resolvers.addAll(resolvers);
        extended.resolvers.add(resolver);
        return extended;
    }
}
