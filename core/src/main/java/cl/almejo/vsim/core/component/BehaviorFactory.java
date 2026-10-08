package cl.almejo.vsim.core.component;

import java.util.Map;

@FunctionalInterface
public interface BehaviorFactory {
    /** @param parameters already resolved by {@link ComponentType#resolveParameters(Map)} */
    Behavior create(Map<String, Object> parameters);
}
