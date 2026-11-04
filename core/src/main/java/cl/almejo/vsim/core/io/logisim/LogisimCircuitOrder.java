package cl.almejo.vsim.core.io.logisim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The circuits of a Logisim project in the order their custom components can be made: every circuit after the ones it
 * uses (leaves first), file order otherwise. Circuits that use themselves, directly or through others, are left out
 * with an INVALID note each cycle (Logisim refuses them too); the circuits that use them stay, and their instances of
 * the missing ones are the importer's to report. An instance of a circuit the file does not have adds no order.
 */
public record LogisimCircuitOrder(List<LogisimCircuit> circuits, List<ImportNote> notes) {

    private static final String CHAIN_SEPARATOR = " › ";

    public LogisimCircuitOrder {
        circuits = List.copyOf(circuits);
        notes = List.copyOf(notes);
    }

    public static LogisimCircuitOrder of(LogisimProject project) {
        Map<String, LogisimCircuit> byName = new LinkedHashMap<>();
        project.circuits().forEach(circuit -> byName.putIfAbsent(circuit.name(), circuit));
        Walk walk = new Walk(byName);
        byName.keySet().forEach(walk::visit);
        List<LogisimCircuit> ordered = walk.finished.stream()
                .filter(name -> !walk.recursive.contains(name))
                .map(byName::get)
                .toList();
        return new LogisimCircuitOrder(ordered, walk.notes);
    }

    /** A depth-first walk over «uses» edges: a circuit finishes after all the circuits it uses. */
    private static final class Walk {
        private final Map<String, LogisimCircuit> byName;
        private final List<String> finished = new ArrayList<>();
        private final Set<String> done = new HashSet<>();
        private final Set<String> recursive = new HashSet<>();
        private final LinkedHashSet<String> path = new LinkedHashSet<>();
        private final List<ImportNote> notes = new ArrayList<>();

        Walk(Map<String, LogisimCircuit> byName) {
            this.byName = byName;
        }

        void visit(String name) {
            if (done.contains(name)) {
                return;
            }
            path.add(name);
            for (String used : uses(byName.get(name))) {
                if (path.contains(used)) {
                    cycle(used);
                } else if (byName.containsKey(used)) {
                    visit(used);
                }
            }
            path.remove(name);
            done.add(name);
            finished.add(name);
        }

        /** The circuits on the path from {@code start} to the current one use each other: all of them are out. */
        private void cycle(String start) {
            List<String> members = new ArrayList<>(path);
            members = members.subList(members.indexOf(start), members.size());
            recursive.addAll(members);
            List<String> chain = new ArrayList<>(members);
            chain.add(start);
            notes.add(ImportNote.about(ImportNote.Kind.INVALID, members.getLast(), LogisimNotes.RECURSIVE_CIRCUIT,
                    String.join(CHAIN_SEPARATOR, chain)));
        }

        /** The circuits {@code circuit} has instances of, once each, in file order. */
        private static Set<String> uses(LogisimCircuit circuit) {
            Set<String> used = new LinkedHashSet<>();
            circuit.components().stream().filter(LogisimComponent::isSubcircuit)
                    .forEach(component -> used.add(component.name()));
            return used;
        }
    }
}
