package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.function.Function;

/**
 * The visible name of a component type, as the palette and the component tree show it (the definition name for user
 * components; the raw id when the type is unknown). The find bar and the tree's filter match on it, so they agree.
 */
public final class TypeNames {

    private TypeNames() {
    }

    public static String of(String typeId, ComponentTypeRegistry registry) {
        return registry.find(typeId).map(type -> Messages.componentName(type)).orElse(typeId);
    }

    /** {@link #of} for every type id of {@code registry}. */
    public static Function<String, String> in(ComponentTypeRegistry registry) {
        return typeId -> of(typeId, registry);
    }
}
