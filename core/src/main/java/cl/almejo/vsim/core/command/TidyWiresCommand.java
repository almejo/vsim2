package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.wiring.TidyPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a {@link TidyPlan} as one undoable step. A wire that the plan trims or merges appears in both lists under
 * the same id, so apply removes first and adds second; the replacement takes the draw order of the wire it replaces,
 * and revert puts every removed wire back at its original order.
 */
public final class TidyWiresCommand implements Command {

    private record RemovedWire(Wire wire, long order) {
    }

    private final TidyPlan plan;
    private final List<RemovedWire> removedWires = new ArrayList<>();
    private List<Probe> probesBefore = List.of();

    public TidyWiresCommand(TidyPlan plan) {
        this.plan = plan;
    }

    /** The plan this command applies. */
    public TidyPlan plan() {
        return plan;
    }

    @Override
    public void apply(CircuitDocument document) {
        removedWires.clear();
        probesBefore = document.probes();
        document.batch(() -> {
            for (Wire wire : plan.removed()) {
                if (document.wire(wire.id()).isPresent()) {
                    long order = document.orderOf(wire.id());
                    document.remove(wire.id());
                    removedWires.add(new RemovedWire(wire, order));
                }
            }
            for (Wire wire : plan.added()) {
                RemovedWire replaced = removedWires.stream()
                        .filter(entry -> entry.wire().id().equals(wire.id())).findFirst().orElse(null);
                if (replaced == null) {
                    document.add(wire);
                } else {
                    document.restore(wire, replaced.order());
                }
            }
        });
        if (!plan.probeRetargets().isEmpty()) {
            document.setProbes(retargeted(probesBefore));
        }
    }

    @Override
    public void revert(CircuitDocument document) {
        document.batch(() -> {
            for (Wire wire : plan.added()) {
                document.remove(wire.id());
            }
            removedWires.forEach(entry -> document.restore(entry.wire(), entry.order()));
        });
        document.setProbes(probesBefore);
    }

    private List<Probe> retargeted(List<Probe> probes) {
        List<Probe> result = new ArrayList<>();
        for (Probe probe : probes) {
            if (probe.target() instanceof ProbeTarget.WireTarget target) {
                ElementId replacement = plan.probeRetargets().get(target.wireId());
                if (replacement != null) {
                    result.add(new Probe(probe.id(), new ProbeTarget.WireTarget(replacement), probe.name()));
                    continue;
                }
            }
            result.add(probe);
        }
        return result;
    }

    @Override
    public String description() {
        return "command.tidyWires";
    }
}
