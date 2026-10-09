package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds a wire, merging it with existing collinear wires that overlap it with positive length.
 * Wires that only touch at an endpoint are kept separate (their shared endpoint may be a junction).
 * An overlapping wire is not absorbed when merging would turn an endpoint (of the new wire or of an
 * absorbed wire) into an interior point of the merged wire while that point lies on another wire:
 * the endpoint is what joins that other wire, so absorbing it would break the connection.
 */
public final class AddWireCommand implements Command {

    private final Wire wire;
    private List<Wire> absorbed = List.of();
    private Wire added;

    public AddWireCommand(Wire wire) {
        this.wire = wire;
    }

    @Override
    public void apply(CircuitDocument document) {
        List<Wire> existingWires = document.wires();
        List<Wire> toAbsorb = new ArrayList<>();
        Wire merged = wire;
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Wire existing : existingWires) {
                if (toAbsorb.contains(existing) || !existing.overlapsCollinear(merged)) {
                    continue;
                }
                Wire candidate = span(merged, existing);
                if (keepsJunctions(candidate, existing, toAbsorb, existingWires)) {
                    toAbsorb.add(existing);
                    merged = candidate;
                    grew = true;
                }
            }
        }
        boolean idTaken = document.element(wire.id()).isPresent()
                && toAbsorb.stream().noneMatch(existing -> existing.id().equals(wire.id()));
        if (idTaken) {
            throw new IllegalArgumentException("duplicate element id " + wire.id());
        }
        Wire result = merged;
        document.batch(() -> {
            toAbsorb.forEach(existing -> document.remove(existing.id()));
            document.add(result);
        });
        absorbed = List.copyOf(toAbsorb);
        added = result;
    }

    private boolean keepsJunctions(Wire candidate, Wire existing, List<Wire> toAbsorb, List<Wire> existingWires) {
        List<GridPoint> endpoints = new ArrayList<>(List.of(wire.start(), wire.end(), existing.start(), existing.end()));
        toAbsorb.forEach(absorbedWire -> endpoints.addAll(List.of(absorbedWire.start(), absorbedWire.end())));
        for (GridPoint endpoint : endpoints) {
            boolean interior = candidate.contains(endpoint)
                    && !endpoint.equals(candidate.start()) && !endpoint.equals(candidate.end());
            if (!interior) {
                continue;
            }
            for (Wire other : existingWires) {
                if (other != existing && !toAbsorb.contains(other) && other.contains(endpoint)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            document.remove(added.id());
            absorbed.forEach(document::add);
        });
    }

    private static Wire span(Wire first, Wire second) {
        GridPoint start = first.start().compareTo(second.start()) <= 0 ? first.start() : second.start();
        GridPoint end = first.end().compareTo(second.end()) >= 0 ? first.end() : second.end();
        return first.withEndpoints(start, end);
    }

    @Override
    public String description() {
        return "command.wire";
    }
}
