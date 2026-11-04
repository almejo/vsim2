package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.MultiBitOrientation;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.FollowPlan;
import cl.almejo.vsim.core.wiring.PinMoves;
import cl.almejo.vsim.core.wiring.WireFollow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Sets a part's parameters and makes the wires of the pins the change moves follow them, as one undo step: the
 * Teclado's «Ancho» or a Terminal's rows typed in the inspector, a gate's «Entradas» (its output moves down from six
 * inputs), a profile chosen, a value an AI edit sets. Dragging an edge ({@link ResizeComponentCommand}) is this same
 * command with its own name in «Deshacer …».
 *
 * <p>A pin follows the pin with the same name in the new pinout (the n-th pin of a name follows the n-th), the way
 * probes follow ({@link ChangeParametersCommand}): a gate's {@code out} is the last pin, so its index changes with the
 * inputs. The wires are planned with {@link WireFollow#plan(CircuitDocument, ComponentTypeRegistry, PinMoves, Supplier)}
 * against the document as it is before the change. A pin the change removes moves no wire: its wires stay where they
 * were, as before wires followed edits. A pin that would land where a removed pin was keeps its wires where they are
 * too, so a gate going from three inputs to two never joins the drivers of {@code in1} and {@code in2}. A wire that
 * follows its pin is routed off the places of the pins the change adds, so it leaves no corner on a new pin (a
 * splitter whose {@code b0} moves down while a new {@code b4-7} takes its row).
 */
public final class ChangeParametersWithWiresCommand implements Command {

    private final CompositeCommand steps;
    private final FollowPlan plan;
    private final String description;

    private ChangeParametersWithWiresCommand(ChangeParametersCommand parameters, FollowPlan plan, String description) {
        this.plan = plan;
        this.description = description;
        // A move of nothing by (0, 0) only applies the plan's wires: replaced, removed and added, reverted exactly.
        this.steps = new CompositeCommand(description, plan.isEmpty() ? List.of(parameters)
                : List.of(parameters, new MoveWithWiresCommand(Set.of(), 0, 0, plan)));
    }

    /** {@link #of(CircuitDocument, ComponentTypeRegistry, ElementId, Map, String)} named «Configurar». */
    public static ChangeParametersWithWiresCommand of(CircuitDocument document, ComponentTypeRegistry registry,
                                                      ElementId id, Map<String, Object> rawParameters) {
        return of(document, registry, id, rawParameters, ChangeParametersCommand.CONFIGURE);
    }

    /**
     * Sets {@code id}'s parameters to {@code rawParameters} (normalized by its type; invalid values throw
     * IllegalArgumentException), planned now on {@code document}; new wires take the document's ids. The edit is named
     * {@code description} in «Deshacer …» (a bundle key such as {@code command.configure}).
     */
    public static ChangeParametersWithWiresCommand of(CircuitDocument document, ComponentTypeRegistry registry,
                                                      ElementId id, Map<String, Object> rawParameters,
                                                      String description) {
        Component component = document.component(id).orElseThrow();
        ComponentType type = registry.typeOf(component);
        ChangeParametersCommand parameters = new ChangeParametersCommand(id, type, rawParameters, description);
        return new ChangeParametersWithWiresCommand(parameters,
                wirePlan(document, registry, component, rawParameters, document::newWireId), description);
    }

    /**
     * How the wires would follow giving {@code component} {@code rawParameters}; the new wires take their ids from
     * {@code newWireIds}, so a preview leaves the document alone.
     */
    static FollowPlan wirePlan(CircuitDocument document, ComponentTypeRegistry registry, Component component,
                               Map<String, Object> rawParameters, Supplier<ElementId> newWireIds) {
        PinMoves moves = pinMoves(registry, component, rawParameters);
        if (moves.after().isEmpty()) {
            return FollowPlan.EMPTY;
        }
        return WireFollow.plan(document, registry, moves, newWireIds);
    }

    /**
     * Where each pin of {@code component} that keeps its name goes, when that is somewhere else, and where the pins
     * the change adds will be (the wires that follow keep off them).
     */
    private static PinMoves pinMoves(ComponentTypeRegistry registry, Component component,
                                     Map<String, Object> rawParameters) {
        ComponentType before = registry.typeOf(component);
        Component changed = MultiBitOrientation.withParameters(component,
                before.resolveParameters(rawParameters), before);
        ComponentType after = registry.typeOf(changed);
        List<Integer> successors = successors(before, after);
        Set<GridPoint> removedPinPlaces = new HashSet<>();
        for (int pinIndex = 0; pinIndex < before.pinCount(); pinIndex++) {
            if (successors.get(pinIndex) < 0) {
                removedPinPlaces.add(before.pinLocation(component, pinIndex));
            }
        }
        Map<PinRef, GridPoint> moves = new LinkedHashMap<>();
        for (int pinIndex = 0; pinIndex < before.pinCount(); pinIndex++) {
            if (successors.get(pinIndex) < 0) {
                continue;
            }
            GridPoint target = after.pinLocation(changed, successors.get(pinIndex));
            if (!target.equals(before.pinLocation(component, pinIndex)) && !removedPinPlaces.contains(target)) {
                moves.put(new PinRef(component.id(), pinIndex), target);
            }
        }
        Set<GridPoint> addedPinPlaces = new HashSet<>();
        for (int pinIndex = 0; pinIndex < after.pinCount(); pinIndex++) {
            if (!successors.contains(pinIndex)) {
                addedPinPlaces.add(after.pinLocation(changed, pinIndex));
            }
        }
        return new PinMoves(moves, addedPinPlaces);
    }

    /**
     * For each pin of {@code before}, the index of the pin of {@code after} with the same name and the same
     * occurrence of that name, or -1 when {@code after} has no such pin (the pin is removed).
     */
    private static List<Integer> successors(ComponentType before, ComponentType after) {
        Map<String, List<Integer>> afterIndicesByName = new HashMap<>();
        for (int pinIndex = 0; pinIndex < after.pinCount(); pinIndex++) {
            afterIndicesByName.computeIfAbsent(after.pin(pinIndex).name(), name -> new ArrayList<>()).add(pinIndex);
        }
        Map<String, Integer> occurrences = new HashMap<>();
        List<Integer> successors = new ArrayList<>();
        for (int pinIndex = 0; pinIndex < before.pinCount(); pinIndex++) {
            PinSpec pin = before.pin(pinIndex);
            int occurrence = occurrences.merge(pin.name(), 1, Integer::sum) - 1;
            List<Integer> candidates = afterIndicesByName.getOrDefault(pin.name(), List.of());
            successors.add(occurrence < candidates.size() ? candidates.get(occurrence) : -1);
        }
        return successors;
    }

    /** How the wires follow this edit; empty when it moves no pin. */
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
        return description;
    }
}
