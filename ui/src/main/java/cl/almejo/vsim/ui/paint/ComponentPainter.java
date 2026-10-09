package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;

import java.awt.Graphics2D;
import java.util.Map;

/** Draws one kind of component in local document coordinates (origin = component origin, 8 px per grid unit). */
@FunctionalInterface
public interface ComponentPainter {
    void paint(Graphics2D graphics, ComponentType type, Map<String, Object> parameters, VisibleState state, ColorScheme scheme);
}
