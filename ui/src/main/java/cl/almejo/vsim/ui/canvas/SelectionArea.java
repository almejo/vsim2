package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import cl.almejo.vsim.ui.paint.InterfacePinPainter;

import java.awt.geom.Rectangle2D;

/**
 * What a component shows for selecting it, in document pixels: its body plus a bus pin's value box (drawn outside the
 * body). The selection outline encloses it and a rubber band must enclose all of it. Labels are not part of it: they
 * are separate text that moves on its own.
 */
public final class SelectionArea {

    private SelectionArea() {
    }

    public static Rectangle2D of(Component component, ComponentTypeRegistry registry) {
        return of(registry.typeOf(component), component);
    }

    public static Rectangle2D of(ComponentType type, Component component) {
        Rectangle2D body = ComponentDrawing.documentBounds(type, component);
        return InterfacePinPainter.valueBox(type, component)
                .map(box -> body.createUnion(box))
                .orElse(body);
    }
}
