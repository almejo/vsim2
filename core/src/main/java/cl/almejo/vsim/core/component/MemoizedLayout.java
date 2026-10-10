package cl.almejo.vsim.core.component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the shape of each set of resolved parameters, so the pin locations and bounds of a parameterized
 * component (asked on every paint and hit test) do not run its layout again. Keeps at most {@link #LIMIT} shapes per
 * type. Equal to another memo of the same layout, so types stay equal however often they are built.
 */
final class MemoizedLayout implements ComponentLayout {

    static final int LIMIT = 256;

    private final ComponentLayout layout;
    private final Map<Map<String, Object>, ComponentShape> shapes = new ConcurrentHashMap<>();

    private MemoizedLayout(ComponentLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    static ComponentLayout of(ComponentLayout layout) {
        return layout instanceof MemoizedLayout || layout instanceof SpecializedLayout ? layout
                : new MemoizedLayout(layout);
    }

    @Override
    public ComponentShape shapeFor(Map<String, Object> resolvedParameters) {
        ComponentShape known = shapes.get(resolvedParameters);
        if (known != null) {
            return known;
        }
        ComponentShape shape = layout.shapeFor(resolvedParameters);
        if (shapes.size() < LIMIT) {
            shapes.putIfAbsent(Map.copyOf(resolvedParameters), shape);
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
