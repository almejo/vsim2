package cl.almejo.vsim.core;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.Map;

/** Builders that put wires and gates straight into a {@link CircuitDocument}, for the tests that need a circuit. */
public final class DocumentFixtures {

    private DocumentFixtures() {
    }

    /** Adds a wire from (startX, startY) to (endX, endY) to {@code document} and returns it. */
    public static Wire wire(CircuitDocument document, int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
        document.add(wire);
        return wire;
    }

    /** Like {@link #wire}, but returns the wire's id. */
    public static ElementId wireId(CircuitDocument document, int startX, int startY, int endX, int endY) {
        return wire(document, startX, startY, endX, endY).id();
    }

    /** Adds a NOT gate (a Buffer with «Negar salida») at (x, y), unrotated, to {@code document} and returns it. */
    public static Component gate(ComponentTypeRegistry registry, CircuitDocument document, int x, int y) {
        Component component = registry.require(StandardComponentTypes.BUFFER).newComponent(document.newComponentId(),
                new GridPoint(x, y), Rotation.R0, GateParameters.NEGATED, "");
        document.add(component);
        return component;
    }

    /** Adds an unlocked, untinted frame titled "Block" to {@code document} and returns it. */
    public static Frame frame(CircuitDocument document, int x, int y, int width, int height) {
        Frame frame = new Frame(document.newFrameId(), new GridPoint(x, y), width, height, "Block", FrameTint.NONE,
                false);
        document.add(frame);
        return frame;
    }

    /** Adds a component of {@code typeId} at (x, y), unrotated and without parameters, to {@code document}. */
    public static Component place(ComponentTypeRegistry registry, CircuitDocument document, String typeId, int x,
                                  int y, String label) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), label);
        document.add(component);
        return component;
    }
}
