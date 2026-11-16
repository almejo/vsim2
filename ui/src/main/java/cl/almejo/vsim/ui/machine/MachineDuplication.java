package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.CreateMachineCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionNames;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * «Duplicar como otra máquina» (hito 3, decision 4): a variant of a machine that is a machine of its own, not one more
 * copy of the same. It gets a fresh id (never one of the circuit or of «Mis componentes», as a new machine's,
 * {@link MachineCreation}), the name the components' rule gives («Semáforo» → «Semáforo 2», not the states' «(2)»),
 * and everything else of the original: its diagram, its pins, its tests, its notes, its face and its description. Its
 * revision starts again, as a new component's.
 */
final class MachineDuplication {

    /** Grid units left free between the original piece and its duplicate, and the step tried to the right. */
    private static final int GAP = 2;
    /** How many steps to the right are tried before the duplicate is put at the first one anyway. */
    private static final int MAXIMUM_STEPS = 500;

    private MachineDuplication() {
    }

    /**
     * The machine {@code original} duplicated into {@code circuit} (the whole circuit, whose definitions include
     * those of every component inside it): a fresh id and the next free name, neither taken in {@code circuit} nor in
     * {@code library}.
     *
     * @throws IllegalArgumentException when {@code original} is not a machine
     */
    static ComponentDefinition copyOf(ComponentDefinition original, CircuitDocument circuit,
                                      ComponentLibrary library) {
        if (!original.isMachine()) {
            throw new IllegalArgumentException(original.id() + " is not a state machine");
        }
        Set<String> takenIds = new HashSet<>(library.ids());
        List<String> takenNames = new ArrayList<>();
        circuit.definitions().forEach(definition -> {
            takenIds.add(definition.id());
            takenNames.add(definition.name());
        });
        library.entries().forEach(entry -> takenNames.add(entry.root().name()));
        return new ComponentDefinition(MachineCreation.freeId(takenIds),
                DefinitionNames.unique(original.name(), takenNames), original.description(), original.size(),
                original.pinout(), original.body(), original.appearance(), 1, original.machine());
    }

    /**
     * The command that puts a duplicate of {@code piece}'s machine in {@code place}'s circuit and its piece at the
     * first free spot to the right of {@code piece}, in one undo step (not executed; its
     * {@link CreateMachineCommand#instanceId()} is the new piece once it is).
     *
     * @throws IllegalArgumentException when {@code piece} is not a machine's piece
     */
    static CreateMachineCommand duplicate(OpenDocument place, Component piece, ComponentLibrary library) {
        ComponentDefinition original = SubcircuitTypes.definitionOf(place.registry().typeOf(piece))
                .filter(ComponentDefinition::isMachine)
                .orElseThrow(() -> new IllegalArgumentException(piece.id() + " is not a machine's piece"));
        CircuitDocument circuit = MachineNaming.circuitOf(place).document();
        ComponentDefinition copy = copyOf(original, circuit, library);
        return new CreateMachineCommand(copy, spotRightOf(place.document(), place.registry(), piece, copy.size()));
    }

    /**
     * The top-left, at {@code piece}'s height, of the first spot to its right where a piece of {@code size} overlaps
     * no part and keeps {@link #GAP} from them.
     */
    private static GridPoint spotRightOf(CircuitDocument document, ComponentTypeRegistry registry, Component piece,
                                         GridSize size) {
        Bounds original = ElementBounds.of(piece, registry);
        List<Bounds> taken = document.components().stream()
                .map(component -> ElementBounds.of(component, registry)).toList();
        int y = original.y();
        GridPoint first = new GridPoint(original.maximumX() + GAP, y);
        for (int step = 0; step < MAXIMUM_STEPS; step++) {
            GridPoint candidate = first.translate(step * GAP, 0);
            Bounds around = grown(Bounds.rotated(candidate, size, Rotation.R0));
            if (taken.stream().noneMatch(around::intersects)) {
                return candidate;
            }
        }
        return first;
    }

    /** {@code bounds} with {@link #GAP} - 1 more on every side: touching parts are left one unit apart. */
    private static Bounds grown(Bounds bounds) {
        int margin = GAP - 1;
        return new Bounds(bounds.x() - margin, bounds.y() - margin, bounds.width() + 2 * margin,
                bounds.height() + 2 * margin);
    }
}
