package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where each bus (a net wider than one bit) writes its value: once, at the middle of its longest wire (the first in
 * document order on a tie), only while the snapshot belongs to the shown netlist and the net has no width mismatch.
 */
public final class BusLabels {

    private static final double UNIT = GridPoint.PIXELS_PER_UNIT;

    /** {@code text} for {@code net}, anchored at document pixel ({@code x}, {@code y}) of a horizontal or vertical wire. */
    public record BusLabel(NetId net, String text, double x, double y, boolean vertical) {
    }

    private BusLabels() {
    }

    public static List<BusLabel> of(RenderState state) {
        if (state.snapshot().token() != state.netlistToken()) {
            return List.of();
        }
        Set<String> mismatched = new HashSet<>();
        state.snapshot().widthMismatches().forEach(mismatch -> mismatched.add(mismatch.netId()));
        List<BusLabel> labels = new ArrayList<>();
        for (Net net : state.netlist().nets()) {
            if (net.width() < 2 || net.widthMismatch() || mismatched.contains(net.id().value())) {
                continue;
            }
            Value value = state.snapshot().netValues().get(net.id().value());
            Optional<Wire> longest = longestWire(state.document(), net);
            if (value == null || longest.isEmpty()) {
                continue;
            }
            Wire wire = longest.get();
            labels.add(new BusLabel(net.id(), BusValueText.of(value), (wire.start().x() + wire.end().x()) * UNIT / 2,
                    (wire.start().y() + wire.end().y()) * UNIT / 2, wire.start().x() == wire.end().x()));
        }
        return labels;
    }

    private static Optional<Wire> longestWire(CircuitDocument document, Net net) {
        Wire longest = null;
        for (Wire wire : document.wires()) {
            if (net.wires().contains(wire.id()) && (longest == null || length(wire) > length(longest))) {
                longest = wire;
            }
        }
        return Optional.ofNullable(longest);
    }

    private static int length(Wire wire) {
        return Math.abs(wire.end().x() - wire.start().x()) + Math.abs(wire.end().y() - wire.start().y());
    }
}
