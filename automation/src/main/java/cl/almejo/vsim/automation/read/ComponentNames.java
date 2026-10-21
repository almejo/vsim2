package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.core.component.ComponentType;

/**
 * The names the palette shows for a component type, which live in the editor's bundles: a user component goes by its
 * definition's name in both languages.
 */
public interface ComponentNames {

    String spanish(ComponentType type);

    String english(ComponentType type);
}
