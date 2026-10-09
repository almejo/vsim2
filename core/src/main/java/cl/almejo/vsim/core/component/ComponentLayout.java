package cl.almejo.vsim.core.component;

import java.util.Map;

/** Computes the size and pins of a type whose geometry depends on its parameters (bus widths). */
@FunctionalInterface
public interface ComponentLayout {
    /** @param resolvedParameters already resolved by {@link ComponentType#resolveParameters(Map)} */
    ComponentShape shapeFor(Map<String, Object> resolvedParameters);
}
