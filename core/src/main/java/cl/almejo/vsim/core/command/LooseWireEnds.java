package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.RemovedPinWire;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The wire ends a machine edit leaves on a removed pin, pulled back one grid unit from the piece so the pins that now
 * take that spot do not touch them (hito 3 review, I1). Turning «Como un bus» on puts {@code RST} where {@code y}
 * was: with {@code y}'s loose wire still ending there the planner would have to keep the piece's old shape (or join
 * {@code RST} to {@code y}'s switch); with the end one unit away the piece takes the machine's fresh face, the wires
 * of {@code RST} and {@code CLK} follow, and {@code y}'s wire is seen loose right next to the box.
 * <p>
 * Only plain cases are pulled back: an end that is the wire's own (not one through the pin), on a wire longer than one
 * unit, and only when nothing else changes electrically (the pieces' nets stay as they were but for the removed
 * pins); otherwise {@link #of} is empty and the edit keeps the planner's old-shape fallback.
 */
final class LooseWireEnds {

    /** The wires as they were and as they are once pulled back, in the same order. */
    private final List<Wire> before;
    private final List<Wire> after;
    /** Where each loose end went, by the end it had at the removed pin. */
    private final Map<GridPoint, GridPoint> moved;

    private LooseWireEnds(List<Wire> before, List<Wire> after, Map<GridPoint, GridPoint> moved) {
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
        this.moved = Map.copyOf(moved);
    }

    /**
     * The wire ends of {@code loose} (on the circuit's own pieces) pulled back, when every one can be and doing so
     * changes no net but to free the removed pins of {@code previous}; empty otherwise (nothing to pull back too).
     */
    static Optional<LooseWireEnds> of(CircuitDocument document, ComponentTypeRegistry registry,
                                      ComponentDefinition previous, List<RemovedPinWire> loose) {
        List<Wire> before = new ArrayList<>();
        List<Wire> after = new ArrayList<>();
        Map<GridPoint, GridPoint> moved = new LinkedHashMap<>();
        for (RemovedPinWire end : loose) {
            for (Wire wire : document.wires()) {
                if (!wire.start().equals(end.position()) && !wire.end().equals(end.position())) {
                    continue;
                }
                Optional<Wire> pulled = pulledBack(wire, end.position());
                if (pulled.isEmpty() || before.contains(wire)) {
                    return Optional.empty();
                }
                before.add(wire);
                after.add(pulled.get());
                moved.putIfAbsent(end.position(), otherEndNear(pulled.get(), end.position()));
            }
        }
        if (before.isEmpty()) {
            return Optional.empty();
        }
        LooseWireEnds ends = new LooseWireEnds(before, after, moved);
        return ends.onlyFreesTheRemovedPins(document, registry, removedPins(previous, loose))
                ? Optional.of(ends) : Optional.empty();
    }

    /** {@code wire} one unit shorter at {@code end}; empty when it is one unit long. */
    private static Optional<Wire> pulledBack(Wire wire, GridPoint end) {
        GridPoint other = wire.start().equals(end) ? wire.end() : wire.start();
        GridPoint step = new GridPoint(Integer.signum(other.x() - end.x()), Integer.signum(other.y() - end.y()));
        GridPoint pulled = end.plus(step);
        return pulled.equals(other) ? Optional.empty() : Optional.of(new Wire(wire.id(), other, pulled));
    }

    /** The end of {@code pulled} one unit from {@code end}. */
    private static GridPoint otherEndNear(Wire pulled, GridPoint end) {
        return Math.abs(pulled.start().x() - end.x()) + Math.abs(pulled.start().y() - end.y()) == 1
                ? pulled.start() : pulled.end();
    }

    private static Set<PinRef> removedPins(ComponentDefinition previous, List<RemovedPinWire> loose) {
        Set<PinRef> pins = new HashSet<>();
        for (RemovedPinWire end : loose) {
            previous.pinIndexOf(end.pinName()).ifPresent(index -> pins.add(new PinRef(end.piece(), index)));
        }
        return pins;
    }

    /** Whether pulling the ends back leaves every net as it was, once the removed pins are left out. */
    private boolean onlyFreesTheRemovedPins(CircuitDocument document, ComponentTypeRegistry registry,
                                            Set<PinRef> removedPins) {
        CircuitDocument pulled = document.copy();
        after.forEach(pulled::replace);
        return nets(Connectivity.of(document.wires(), document, registry), removedPins)
                .equals(nets(Connectivity.of(pulled.wires(), pulled, registry), removedPins));
    }

    private static Set<Set<PinRef>> nets(Connectivity connectivity, Set<PinRef> removedPins) {
        Set<Set<PinRef>> nets = new HashSet<>();
        for (Set<PinRef> net : connectivity.pinNets()) {
            Set<PinRef> kept = new HashSet<>(net);
            kept.removeAll(removedPins);
            if (kept.size() > 1) {
                nets.add(kept);
            }
        }
        return nets;
    }

    void apply(CircuitDocument document) {
        document.batch(() -> after.forEach(document::replace));
    }

    void revert(CircuitDocument document) {
        document.batch(() -> before.forEach(document::replace));
    }

    /** {@code loose} with each end where it was pulled back to (the others as they are). */
    List<RemovedPinWire> movedEnds(List<RemovedPinWire> loose) {
        return loose.stream().map(end -> moved.containsKey(end.position()) && end.containingDefinition().isEmpty()
                ? new RemovedPinWire(end.containingDefinition(), end.piece(), end.pinName(),
                        moved.get(end.position()))
                : end).toList();
    }
}
