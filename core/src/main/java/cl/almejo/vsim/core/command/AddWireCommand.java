package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds a wire, merging it with existing collinear wires that overlap it with positive length.
 * Wires that only touch at an endpoint are kept separate (their shared endpoint may be a junction).
 */
public final class AddWireCommand implements Command {

    private final Wire wire;
    private final List<Wire> absorbed = new ArrayList<>();
    private Wire added;

    public AddWireCommand(Wire wire) {
        this.wire = wire;
    }

    @Override
    public void apply(CircuitDocument document) {
        absorbed.clear();
        Wire merged = wire;
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Wire existing : document.wires()) {
                if (!absorbed.contains(existing) && existing.overlapsCollinear(merged)) {
                    absorbed.add(existing);
                    merged = span(merged, existing);
                    grew = true;
                }
            }
        }
        added = merged;
        document.batch(() -> {
            absorbed.forEach(existing -> document.remove(existing.id()));
            document.add(added);
        });
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
