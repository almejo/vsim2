package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.ProbeNaming;
import cl.almejo.vsim.core.netlist.ProbeResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Removes elements; undo puts each one back at its original paint and file position. The waveform probes follow in the
 * same step ({@link ProbeResolver#afterRemoval}): a probe on a deleted part or wire moves to what survives of its net
 * and is still connected, or goes when nothing is; undo puts the probe list back exactly. {@code registry} resolves the document's components
 * (to compile the nets before the deletion and tell drivers); it is used only when a probe watches a removed element.
 */
public final class RemoveElementsCommand implements Command {

    private record RemovedElement(DocumentElement element, long order) {
    }

    private final List<ElementId> ids;
    /** What the same undo step adds in place of the removed elements (counted as connected for the probes). */
    private final List<DocumentElement> replacements;
    private final ComponentTypeRegistry registry;
    private final ProbeNaming naming;
    private final List<RemovedElement> removed = new ArrayList<>();
    /** The probes before the last apply when it changed them, else null. */
    private List<Probe> probesBefore;

    /** A moved channel with an empty name keeps it ({@link ProbeNaming#NONE}): for what no channel watches, and tests. */
    public RemoveElementsCommand(Collection<ElementId> ids, ComponentTypeRegistry registry) {
        this(ids, registry, ProbeNaming.NONE);
    }

    /**
     * {@code naming}: the name a channel with an empty name shows (the editor's, in the user's language); a channel
     * moved by the deletion stores the one it showed before, so its row keeps its name.
     */
    public RemoveElementsCommand(Collection<ElementId> ids, ComponentTypeRegistry registry, ProbeNaming naming) {
        this(ids, List.of(), registry, naming);
    }

    private RemoveElementsCommand(Collection<ElementId> ids, Collection<DocumentElement> replacements,
                                  ComponentTypeRegistry registry, ProbeNaming naming) {
        this.ids = List.copyOf(ids);
        this.replacements = List.copyOf(replacements);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.naming = Objects.requireNonNull(naming, "naming");
    }

    /**
     * Removes {@code ids} when a later command of the same undo step adds {@code replacements} in their place (the
     * instance and bridge wires of «Crear componente»): a probe stays on a survivor that those connect, as it will be
     * once the whole step is done.
     */
    public static RemoveElementsCommand replacedBy(Collection<ElementId> ids,
                                                   Collection<? extends DocumentElement> replacements,
                                                   ComponentTypeRegistry registry, ProbeNaming naming) {
        return new RemoveElementsCommand(ids, List.copyOf(replacements), registry, naming);
    }

    @Override
    public void apply(CircuitDocument document) {
        removed.clear();
        probesBefore = null;
        List<Probe> probesAfter = probesAfterRemoval(document);
        // The probes first: a moved probe's new target is in its old net, so it resolves both on the nets of now and
        // on the recompiled ones, and the engine never gets it without a net (no false gap between the two requests).
        if (!probesAfter.equals(document.probes())) {
            probesBefore = document.probes();
            document.setProbes(probesAfter);
        }
        document.batch(() -> {
            for (ElementId id : ids) {
                if (document.element(id).isPresent()) {
                    long order = document.orderOf(id);
                    document.remove(id).ifPresent(element -> removed.add(new RemovedElement(element, order)));
                }
            }
        });
    }

    /**
     * The probe list once the elements are gone, computed on the document as it is before the removal and on a copy
     * without them (what is still connected); unchanged when the circuit does not compile (its probes stay,
     * unresolved, rather than vanish).
     */
    private List<Probe> probesAfterRemoval(CircuitDocument document) {
        List<Probe> probes = document.probes();
        Set<ElementId> removedIds = Set.copyOf(ids);
        if (probes.stream().noneMatch(probe -> ProbeResolver.watchesRemoved(probe.target(), removedIds, document))) {
            return probes;
        }
        CircuitDocument without = document.copy();
        ids.forEach(without::remove);
        replacements.forEach(without::add);
        Netlist netlist;
        Netlist netlistAfter;
        try {
            NetlistCompiler compiler = new NetlistCompiler(registry);
            netlist = compiler.compile(document);
            netlistAfter = compiler.compile(without);
        } catch (RuntimeException exception) {
            // A circuit that does not compile keeps its probes: they show «(sin conexión)» rather than vanish.
            return probes;
        }
        return ProbeResolver.afterRemoval(probes, removedIds, document, netlist, netlistAfter, registry, naming);
    }

    /** The elements first, then the probes, so the old targets exist again when the probes come back. */
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
