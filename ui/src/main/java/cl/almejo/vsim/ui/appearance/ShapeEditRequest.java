package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.library.ComponentDefinition;

/** Immutable starting state: library watcher changes cannot replace a draft while the modal editor is open. */
public record ShapeEditRequest(ComponentDefinition definition, ComponentAppearance appearance,
                               ShapeEditScope initialScope, boolean switchable, boolean definitionPinsLocked,
                               String lockReason, int affectedInstances, String instanceName) { }
