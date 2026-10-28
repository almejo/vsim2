package cl.almejo.vsim.core.examples;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds example circuits in Java (M3 {@code ExampleCircuits}, M6.1 {@code BundledCircuits}): places components by
 * pin or by bounding box and routes wires, remembering which pins are meant to be joined.
 */
public final class CircuitBuilder {

    private static final Rotation[] ALL_ROTATIONS = Rotation.values();

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final List<List<PinRef>> links = new ArrayList<>();

    public CircuitBuilder(String name) {
        document.setName(name);
        // Instances of the document's own definitions (subcircuit:<id>) are placed like any other component.
        registry.addResolver(SubcircuitTypes.resolverFor(document));
    }

    public CircuitDocument document() {
        return document;
    }

    /** The standard types plus this document's definitions. */
    public ComponentTypeRegistry registry() {
        return registry;
    }

    /** Embeds {@code definition} in the document so its instances can be placed (a user component in the file). */
    public void define(ComponentDefinition definition) {
        document.putDefinition(definition);
    }

    /** Pairs of pins the circuit means to connect; the nets of the document must be exactly their closure. */
    public List<List<PinRef>> links() {
        return links;
    }

    /** Places a component so that its bounding box (after rotation) has its top-left corner at the given point. */
    public Component placeByBox(String typeId, int left, int top, Rotation rotation, Map<String, Object> parameters,
                                String label) {
        GridSize size = registry.require(typeId).size();
        GridPoint farCorner = rotation.apply(new GridPoint(size.width(), size.height()));
        GridPoint position = new GridPoint(left - Math.min(0, farCorner.x()), top - Math.min(0, farCorner.y()));
        return place(typeId, position, rotation, parameters, label);
    }

    /** Places a component so that its pin {@code pinIndex} lands on {@code pinPoint}. */
    public Component placeByPin(String typeId, GridPoint pinPoint, int pinIndex, Rotation rotation,
                                Map<String, Object> parameters, String label) {
        GridPoint local = registry.require(typeId).specializedFor(parameters).pin(pinIndex).position();
        GridPoint rotated = rotation.apply(local);
        return place(typeId, new GridPoint(pinPoint.x() - rotated.x(), pinPoint.y() - rotated.y()), rotation,
                parameters, label);
    }

    private Component place(String typeId, GridPoint position, Rotation rotation, Map<String, Object> parameters,
                            String label) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), position, rotation,
                parameters, label);
        document.add(component);
        return component;
    }

    public GridPoint pin(Component component, int pinIndex) {
        return registry.require(component.typeId()).pinLocation(component, pinIndex);
    }

    /**
     * Places a driver or load straight out of a pin: a wire of {@code length} units leaves the pin and
     * ends on pin 0 of the new component, which is rotated to face the pin.
     */
    public Component attach(Component target, int pinIndex, String typeId, Map<String, Object> parameters,
                            String label, int length) {
        GridPoint direction = target.rotation().apply(localOutward(registry.typeOf(target), target.typeId(), pinIndex));
        GridPoint start = pin(target, pinIndex);
        GridPoint end = start.translate(direction.x() * length, direction.y() * length);
        GridPoint wanted = new GridPoint(-direction.x(), -direction.y());
        Rotation facing = null;
        for (Rotation candidate : ALL_ROTATIONS) {
            if (candidate.apply(localOutward(registry.require(typeId).specializedFor(parameters), typeId, 0)).equals(wanted)) {
                facing = candidate;
            }
        }
        Component attached = placeByPin(typeId, end, 0, facing, parameters, label);
        route(start, end);
        link(target, pinIndex, attached, 0);
        return attached;
    }

    public void route(GridPoint... points) {
        for (int index = 1; index < points.length; index++) {
            if (!points[index - 1].equals(points[index])) {
                document.add(new Wire(document.newWireId(), points[index - 1], points[index]));
            }
        }
    }

    public void link(Component first, int firstPin, Component second, int secondPin) {
        links.add(List.of(new PinRef(first.id(), firstPin), new PinRef(second.id(), secondPin)));
    }

    public TextLabel text(int x, int y, String text) {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(x, y), text);
        document.add(label);
        return label;
    }

    /** Unit vector pointing away from the component body at a pin, before rotation. */
    private GridPoint localOutward(ComponentType type, String typeId, int pinIndex) {
        if (typeId.startsWith("seven-segments")) {
            return new GridPoint(0, -1);
        }
        GridPoint pin = type.pin(pinIndex).position();
        if (pin.x() == 0) {
            return new GridPoint(-1, 0);
        }
        if (pin.x() == type.size().width()) {
            return new GridPoint(1, 0);
        }
        return new GridPoint(0, pin.y() == 0 ? -1 : 1);
    }
}
