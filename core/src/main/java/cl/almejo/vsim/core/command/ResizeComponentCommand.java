package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ResizeSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.FollowPlan;
import cl.almejo.vsim.core.wiring.PinMoves;
import cl.almejo.vsim.core.wiring.WireFollow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Sets the resize parameters of a part ({@link ResizeSpec}: the Teclado's «Ancho», a Terminal's columns and rows) and
 * makes the wires of the pins that move with the edges follow them, as one undo step (Tasks 6b, 7b). The part keeps
 * its position; the wires are planned with
 * {@link WireFollow#plan(CircuitDocument, ComponentTypeRegistry, PinMoves, Supplier)} against the document as it is
 * before the change, the way an appearance edit moves the pins of its pieces.
 */
public final class ResizeComponentCommand implements Command {

    static final String DESCRIPTION = "command.resizePart";

    private final CompositeCommand steps;
    private final FollowPlan plan;

    private ResizeComponentCommand(ChangeParametersCommand parameters, FollowPlan plan) {
        this.plan = plan;
        // A move of nothing by (0, 0) only applies the plan's wires: replaced, removed and added, reverted exactly.
        this.steps = new CompositeCommand(DESCRIPTION, List.of(parameters,
                new MoveWithWiresCommand(Set.of(), 0, 0, plan)));
    }

    /**
     * Sets {@code id}'s resize parameter to {@code value} (it must be in the parameter's range), planned now on
     * {@code document}; new wires take the document's ids.
     *
     * @throws IllegalArgumentException when the part's type has no resize or the value is out of range
     */
    public static ResizeComponentCommand of(CircuitDocument document, ComponentTypeRegistry registry, ElementId id,
                                            int value) {
        return of(document, registry, id, Map.of(firstParameter(document, registry, id), value));
    }

    /**
     * Sets several resize parameters of {@code id} at once (a Terminal's columns and rows, Task 7b), each in its range,
     * as one undo step; planned now on {@code document}, new wires take the document's ids.
     *
     * @throws IllegalArgumentException when the part's type has no resize, a parameter is not one of its axes or a
     *                                  value is out of range
     */
    public static ResizeComponentCommand of(CircuitDocument document, ComponentTypeRegistry registry, ElementId id,
                                            Map<String, Integer> values) {
        Component component = document.component(id).orElseThrow();
        ComponentType type = registry.typeOf(component);
        Map<String, Object> parameters = resizedParameters(type, component, values);
        return new ResizeComponentCommand(new ChangeParametersCommand(id, type, parameters, DESCRIPTION),
                wirePlan(document, registry, component, parameters, document::newWireId));
    }

    /**
     * How the wires would follow resizing {@code id} to {@code value}, for a preview: the new wires take their ids from
     * {@code newWireIds}, so the document is not changed at all.
     */
    public static FollowPlan wirePlan(CircuitDocument document, ComponentTypeRegistry registry, ElementId id,
                                      int value, Supplier<ElementId> newWireIds) {
        return wirePlan(document, registry, id, Map.of(firstParameter(document, registry, id), value), newWireIds);
    }

    /** {@link #wirePlan(CircuitDocument, ComponentTypeRegistry, ElementId, int, Supplier)} for several parameters. */
    public static FollowPlan wirePlan(CircuitDocument document, ComponentTypeRegistry registry, ElementId id,
                                      Map<String, Integer> values, Supplier<ElementId> newWireIds) {
        Component component = document.component(id).orElseThrow();
        ComponentType type = registry.typeOf(component);
        return wirePlan(document, registry, component, resizedParameters(type, component, values), newWireIds);
    }

    private static ResizeSpec resizeOf(ComponentType type) {
        return type.resize().orElseThrow(() -> new IllegalArgumentException(type.id() + " cannot be resized"));
    }

    private static String firstParameter(CircuitDocument document, ComponentTypeRegistry registry, ElementId id) {
        return resizeOf(registry.typeOf(document.component(id).orElseThrow())).parameter();
    }

    private static Map<String, Object> resizedParameters(ComponentType type, Component component,
                                                         Map<String, Integer> values) {
        ResizeSpec resize = resizeOf(type);
        values.forEach((parameter, value) -> {
            ResizeSpec.Axis axis = resize.axisOf(parameter).orElseThrow(() -> new IllegalArgumentException(
                    type.id() + " is not resized by " + parameter));
            if (value < axis.minimum(type) || value > axis.maximum(type)) {
                throw new IllegalArgumentException(parameter + " " + value + " is out of range");
            }
        });
        return resize.parametersWith(component.parameters(), values);
    }

    private static FollowPlan wirePlan(CircuitDocument document, ComponentTypeRegistry registry, Component component,
                                       Map<String, Object> parameters, Supplier<ElementId> newWireIds) {
        ComponentType before = registry.typeOf(component);
        Component resized = component.withParameters(parameters);
        ComponentType after = registry.typeOf(resized);
        Map<PinRef, GridPoint> moves = new LinkedHashMap<>();
        for (int pinIndex = 0; pinIndex < before.pinCount(); pinIndex++) {
            GridPoint target = after.pinLocation(resized, pinIndex);
            if (!target.equals(before.pinLocation(component, pinIndex))) {
                moves.put(new PinRef(component.id(), pinIndex), target);
            }
        }
        return WireFollow.plan(document, registry, new PinMoves(moves), newWireIds);
    }

    /** How the wires follow this resize. */
    public FollowPlan plan() {
        return plan;
    }

    @Override
    public void apply(CircuitDocument document) {
        steps.apply(document);
    }

    @Override
    public void revert(CircuitDocument document) {
        steps.revert(document);
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }
}
