package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;

import java.util.Map;

/**
 * The names the palette shows for a component type, which live in the editor's bundles: a user component goes by its
 * definition's name in both languages. Also the «Descripción» Propiedades shows, so the AI reads the same rules.
 */
public interface ComponentNames {

    String spanish(ComponentType type);

    String english(ComponentType type);

    /**
     * What Propiedades says about a part of {@code type} with the resolved {@code parameters}, in the editor's
     * language: its rules and assumptions. This default knows no bundles, so it gives only a user component's own
     * description (empty for the rest); the editor's names give the built-in texts too.
     */
    default String description(ComponentType type, Map<String, Object> parameters) {
        return SubcircuitTypes.definitionOf(type).map(ComponentDefinition::description).orElse("");
    }
}
