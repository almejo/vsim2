package cl.almejo.vsim.ui.appearance;

import cl.almejo.vsim.core.component.appearance.ComponentAppearance;

/** The completed visual change; the caller rechecks its target before applying one circuit command. */
public record ShapeEditResult(ShapeEditScope scope, ComponentAppearance appearance) { }
