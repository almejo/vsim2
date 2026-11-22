package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes elements and the waveform probes that watch them; undo puts each one back at its original paint and file
 * position, and the probes with them.
 */
public final class RemoveElementsCommand implements Command {

    private record RemovedElement(DocumentElement element, long order) {
    }

    private final List<ElementId> ids;
    private final List<RemovedElement> removed = new ArrayList<>();
    private List<Probe> probesBefore = List.of();

    public RemoveElementsCommand(Collection<ElementId> ids) {
        this.ids = List.copyOf(ids);
    }

    @Override
    public void apply(CircuitDocument document) {
        removed.clear();
        probesBefore = document.probes();
        document.batch(() -> {
            for (ElementId id : ids) {
                if (document.element(id).isPresent()) {
                    long order = document.orderOf(id);
                    document.remove(id).ifPresent(element -> removed.add(new RemovedElement(element, order)));
                }
            }
        });
        Set<ElementId> removedIds = new HashSet<>();
        removed.forEach(entry -> removedIds.add(entry.element().id()));
        List<Probe> kept = probesBefore.stream().filter(probe -> !watchesAny(probe, removedIds)).toList();
        if (kept.size() != probesBefore.size()) {
            document.setProbes(kept);
        }
    }

    private static boolean watchesAny(Probe probe, Set<ElementId> removedIds) {
        return switch (probe.target()) {
            case ProbeTarget.PinTarget pin -> removedIds.contains(pin.componentId());
            case ProbeTarget.WireTarget wire -> removedIds.contains(wire.wireId());
        };
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> removed.forEach(entry -> document.restore(entry.element(), entry.order())));
        document.setProbes(probesBefore);
    }

    @Override
    public String description() {
        return "command.remove";
    }
}
