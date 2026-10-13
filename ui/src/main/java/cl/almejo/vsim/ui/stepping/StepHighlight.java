package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The glow of one «Avanzar» (M6.1-B, spec §4.1): the wires of the top-level nets whose value changed — nested and
 * private net ids are not on the canvas and are skipped — fully lit for {@link #FULL_NANOS}, then fading linearly to
 * nothing at {@link #END_NANOS}. It belongs to the netlist it was built from ({@link #matches}).
 */
public record StepHighlight(Set<ElementId> wires, long netlistToken, long startNanos) {

    public static final long FULL_NANOS = TimeUnit.MILLISECONDS.toNanos(150);
    public static final long END_NANOS = TimeUnit.MILLISECONDS.toNanos(800);

    public StepHighlight {
        wires = Set.copyOf(wires);
    }

    /** The glow of {@code changedNets} on {@code netlist} (compiled at revision {@code netlistToken}), from now. */
    public static StepHighlight of(Set<String> changedNets, Netlist netlist, long netlistToken, long startNanos) {
        Set<ElementId> wires = new HashSet<>();
        for (String netId : changedNets) {
            netlist.net(new NetId(netId)).map(Net::wires).ifPresent(wires::addAll);
        }
        return new StepHighlight(wires, netlistToken, startNanos);
    }

    /** 1 during the first 150 ms, then linearly down to 0 at 800 ms. */
    public double alpha(long nowNanos) {
        long elapsed = nowNanos - startNanos;
        if (elapsed <= FULL_NANOS) {
            return 1;
        }
        if (elapsed >= END_NANOS) {
            return 0;
        }
        return 1 - (elapsed - FULL_NANOS) / (double) (END_NANOS - FULL_NANOS);
    }

    public boolean isOver(long nowNanos) {
        return nowNanos - startNanos >= END_NANOS;
    }

    /** False once the document was recompiled: the wire ids may mean something else now. */
    public boolean matches(long currentNetlistToken) {
        return netlistToken == currentNetlistToken;
    }
}
