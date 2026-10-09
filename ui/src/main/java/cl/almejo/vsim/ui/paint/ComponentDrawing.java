package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Bounds;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;

import static cl.almejo.vsim.ui.paint.PainterSupport.UNIT;

public final class ComponentDrawing {

    private ComponentDrawing() {
    }

    /** Paints {@code component} in document coordinates: moves to its origin and applies its rotation. */
    public static void paint(Graphics2D graphics, ComponentType type, Component component, VisibleState state,
                             ColorScheme scheme, PainterRegistry painters) {
        Graphics2D local = (Graphics2D) graphics.create();
        try {
            local.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            local.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            local.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            local.translate(component.position().x() * UNIT, component.position().y() * UNIT);
            // Rotation.apply matches Java2D's rotate(+90°) on a y-down screen.
            local.rotate(component.rotation().quarterTurns() * Math.PI / 2);
            painters.painterFor(type.id()).paint(local, type, component.parameters(), state, scheme);
        } finally {
            local.dispose();
        }
    }

    public static Rectangle2D documentBounds(ComponentType type, Component component) {
        Bounds bounds = type.bounds(component);
        return new Rectangle2D.Double(bounds.x() * UNIT, bounds.y() * UNIT, bounds.width() * UNIT, bounds.height() * UNIT);
    }
}
