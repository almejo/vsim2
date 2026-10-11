package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link WireCrossings#of} computed once per document revision and grouped by vertical wire, so repaints (marching
 * ants, simulation snapshots) do not repeat the search over every pair of wires. One per renderer; EDT only.
 */
final class CrossingCache {

    private static final Comparator<Crossing> BY_Y = Comparator.comparingInt(crossing -> crossing.point().y());

    private CircuitDocument document;
    private long revision;
    private Map<ElementId, List<Crossing>> gaps = Map.of();

    /** Vertical wire id → its crossings sorted by y; the same instance until the document or its revision changes. */
    Map<ElementId, List<Crossing>> gapsByVerticalWire(CircuitDocument current, ComponentTypeRegistry registry) {
        if (current != document || current.revision() != revision) {
            Map<ElementId, List<Crossing>> grouped = new LinkedHashMap<>();
            for (Crossing crossing : WireCrossings.of(current, registry)) {
                grouped.computeIfAbsent(crossing.verticalWire(), wire -> new ArrayList<>()).add(crossing);
            }
            Map<ElementId, List<Crossing>> sorted = new LinkedHashMap<>();
            grouped.forEach((wire, crossings) -> {
                crossings.sort(BY_Y);
                sorted.put(wire, List.copyOf(crossings));
            });
            gaps = Map.copyOf(sorted);
            document = current;
            revision = current.revision();
        }
        return gaps;
    }
}
