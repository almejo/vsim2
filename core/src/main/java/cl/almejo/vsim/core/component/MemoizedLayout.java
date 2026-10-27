package cl.almejo.vsim.core.component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Remembers the shape of each set of resolved parameters, so the pin locations and bounds of a parameterized
 * component (asked on every paint and hit test) do not run its layout again. Keeps at most {@link #LIMIT} shapes per
 * type. Equal to another memo of the same layout, so types stay equal however often they are built. The
 * {@linkplain ParameterSpec#hidden() hidden} parameters (a memory's contents, possibly megabytes of text) never key a
 * shape, so a layout must not depend on them.
 */
final class MemoizedLayout implements ComponentLayout {

    static final int LIMIT = 256;

    private final ComponentLayout layout;
    private final Set<String> hiddenNames;
    private final Map<Map<String, Object>, ComponentShape> shapes = new ConcurrentHashMap<>();

    private MemoizedLayout(ComponentLayout layout, Set<String> hiddenNames) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.hiddenNames = hiddenNames;
    }

    static ComponentLayout of(ComponentLayout layout, List<ParameterSpec> parameters) {
        if (layout instanceof MemoizedLayout || layout instanceof SpecializedLayout) {
            return layout;
        }
        return new MemoizedLayout(layout, parameters.stream().filter(ParameterSpec::hidden)
                .map(ParameterSpec::name).collect(Collectors.toUnmodifiableSet()));
    }

    /** {@code resolvedParameters} without the hidden ones: what keys a shape. */
    private Map<String, Object> keyOf(Map<String, Object> resolvedParameters) {
        if (hiddenNames.isEmpty()) {
            return resolvedParameters;
        }
        Map<String, Object> key = new HashMap<>(resolvedParameters);
        key.keySet().removeAll(hiddenNames);
        return key;
    }

    @Override
    public ComponentShape shapeFor(Map<String, Object> resolvedParameters) {
        Map<String, Object> key = keyOf(resolvedParameters);
        ComponentShape known = shapes.get(key);
        if (known != null) {
            return known;
        }
        ComponentShape shape = layout.shapeFor(resolvedParameters);
        if (shapes.size() < LIMIT) {
            shapes.putIfAbsent(Map.copyOf(key), shape);
        }
        return shape;
    }

    @Override
    public boolean equals(Object other) {
        return SpecializedLayout.baseOf(other instanceof ComponentLayout layout ? layout : null)
                instanceof MemoizedLayout memo && memo.layout.equals(this.layout);
    }

    @Override
    public int hashCode() {
        return layout.hashCode();
    }
}
