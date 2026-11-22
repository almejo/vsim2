package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.ProbeResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Removes elements; undo puts each one back at its original paint and file position. The waveform probes follow in the
 * same step ({@link ProbeResolver#afterRemoval}): a probe on a deleted part or wire moves to what survives of its net,
 * or goes when nothing does; undo puts the probe list back exactly. {@code registry} resolves the document's components
 * (to compile the nets before the deletion and tell drivers); it is used only when a probe watches a removed element.
 */
public final class RemoveElementsCommand implements Command {

    private record RemovedElement(DocumentElement element, long order) {
    }

    private final List<ElementId> ids;
    private final ComponentTypeRegistry registry;
    private final List<RemovedElement> removed = new ArrayList<>();
    /** The probes before the last apply when it changed them, else null. */
    private List<Probe> probesBefore;

    public RemoveElementsCommand(Collection<ElementId> ids, ComponentTypeRegistry registry) {
        this.ids = List.copyOf(ids);
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void apply(CircuitDocument document) {
        removed.clear();
        probesBefore = null;
        List<Probe> probesAfter = probesAfterRemoval(document);
        document.batch(() -> {
            for (ElementId id : ids) {
                if (document.element(id).isPresent()) {
                    long order = document.orderOf(id);
                    document.remove(id).ifPresent(element -> removed.add(new RemovedElement(element, order)));
                }
            }
        });
        if (!probesAfter.equals(document.probes())) {
            probesBefore = document.probes();
            document.setProbes(probesAfter);
        }
    }

    /**
     * The probe list once the elements are gone, computed on the document as it is before the removal; unchanged when
     * the circuit does not compile (its probes stay, unresolved, rather than vanish).
     */
    private List<Probe> probesAfterRemoval(CircuitDocument document) {
        List<Probe> probes = document.probes();
        Set<ElementId> removedIds = Set.copyOf(ids);
        if (probes.stream().noneMatch(probe -> ProbeResolver.watchesRemoved(probe.target(), removedIds, document))) {
            return probes;
        }
        Netlist netlist;
        try {
            netlist = new NetlistCompiler(registry).compile(document);
        } catch (RuntimeException exception) {
            // A circuit that does not compile keeps its probes: they show «(sin conexión)» rather than vanish.
            return probes;
        }
        return ProbeResolver.afterRemoval(probes, removedIds, document, netlist, registry);
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> removed.forEach(entry -> document.restore(entry.element(), entry.order())));
        if (probesBefore != null) {
            document.setProbes(probesBefore);
        }
    }

    @Override
    public String description() {
        return "command.remove";
    }
}
