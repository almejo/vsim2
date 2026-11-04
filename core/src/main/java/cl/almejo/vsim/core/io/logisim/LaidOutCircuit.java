package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One Logisim circuit laid out on VSim's grid by {@link LogisimLayout}: a document with its parts, wires and texts;
 * the VSim part each placed Logisim component became (its own part, not a NOT added in front of it);
 * the Logisim net (as drawn, {@link LogisimNetAnalysis#netOf}) each placed pin belongs to, a negative number for a pin
 * that must stay alone (or with only the pins sharing that number, as a gate's input and the NOT in front of it);
 * how the pins were reached; and the notes (the parts' mapping notes and the layout's own).
 */
public record LaidOutCircuit(CircuitDocument document, Map<LogisimComponent, ElementId> parts,
                             Map<PinRef, Integer> pinNets, Statistics statistics, List<ImportNote> notes) {

    public LaidOutCircuit {
        Objects.requireNonNull(document, "document");
        parts = Map.copyOf(parts);
        pinNets = Map.copyOf(pinNets);
        Objects.requireNonNull(statistics, "statistics");
        notes = List.copyOf(notes);
    }

    /**
     * How the connected pins met their Logisim ends: landing on the end (or on a wire of their net), through added
     * wire segments, or not at all (no way that touches no other net; noted, the pin stays unconnected); and how
     * many parts took another rotation than their Logisim facing gives (a mirrored part, a crowded place).
     */
    public record Statistics(int pinsOnTheirEnd, int pinsRouted, int addedWires, int unrouted, int turnedParts) {
    }
}
