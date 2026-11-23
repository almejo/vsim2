package cl.almejo.vsim.ui.search;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.function.Function;

/**
 * The visible name of a component type, as the palette and the component tree show it (the definition name for user
 * components; «NAND» for an AND with «Negar salida»; the raw id when the type is unknown). The find bar and the tree's
 * filter match on it, so they agree.
 */
public final class TypeNames {

    private TypeNames() {
    }

    /** The name of {@code nameId}: a type id, or a preset's ({@code nand}, {@link StandardComponentTypes#nameId}). */
    public static String of(String nameId, ComponentTypeRegistry registry) {
        return registry.find(nameId).map(type -> Messages.componentName(type))
                .orElseGet(() -> Messages.componentName(nameId));
    }

    /** The name of {@code component}'s kind: «NAND» for an AND with «Negar salida». */
    public static String of(Component component, ComponentTypeRegistry registry) {
        return of(StandardComponentTypes.nameId(component.typeId(), component.parameters()), registry);
    }

    /** {@link #of(String, ComponentTypeRegistry)} for every name id of {@code registry}. */
    public static Function<String, String> in(ComponentTypeRegistry registry) {
        return typeId -> of(typeId, registry);
    }
}
