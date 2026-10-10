package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.InstancePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Top-level nets that the simulation joins into one through the pins of a template instance (a pass-through inside
 * the component). The engine reports a short circuit or a width mismatch of such a group only under its root's id
 * ({@link FlatNet#aliases()}), so the canvas resolves every top-level net to its group to mark and explain all of it.
 * Found by flattening the document again, which is skipped when the document has no template instance.
 */
public final class TopLevelNets {

    public static final TopLevelNets NONE = new TopLevelNets(Map.of());

    private static final Logger LOGGER = LoggerFactory.getLogger(TopLevelNets.class);

    /** Each merged top-level net id → every top-level id of its group, the reported root first. */
    private final Map<String, List<String>> groups;

    private TopLevelNets(Map<String, List<String>> groups) {
        this.groups = Map.copyOf(groups);
    }

    public static TopLevelNets of(CircuitDocument document, Netlist netlist, ComponentTypeRegistry registry) {
        boolean anyInstance = document.components().stream()
                .anyMatch(component -> SubcircuitTypes.definitionIdOf(component.typeId()).isPresent());
        if (!anyInstance) {
            return NONE;
        }
        try {
            return of(new CircuitFlattener(registry).flatten(document, netlist));
        } catch (RuntimeException exception) {
            // The simulation reports the same failure (OpenDocument.synchronize); nothing is merged then.
            LOGGER.debug("Cannot flatten {} to find merged nets: {}", document.name(), exception.getMessage());
            return NONE;
        }
    }

    public static TopLevelNets of(FlatCircuit circuit) {
        Map<String, List<String>> groups = new HashMap<>();
        for (FlatNet net : circuit.nets()) {
            // The root is the id closest to the top level, so it is top-level whenever any member is.
            List<String> members = Stream.concat(Stream.of(net.id()), net.aliases().stream())
                    .filter(id -> !InstancePath.isNested(id))
                    .toList();
            if (members.size() > 1) {
                members.forEach(member -> groups.put(member, members));
            }
        }
        return groups.isEmpty() ? NONE : new TopLevelNets(groups);
    }

    /** Every top-level id merged with {@code netId}, the reported root first; just {@code netId} when not merged. */
    public List<String> membersOf(String netId) {
        return groups.getOrDefault(netId, List.of(netId));
    }

    /** The id the engine reports the problems of {@code netId} under. */
    public String rootOf(String netId) {
        return membersOf(netId).get(0);
    }
}
