package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * «Connect these two pins» on the candidate document (A§8): tries a few orthogonal routes — a Z through the middle
 * column, the two L's, a Z through the middle row; or exactly the route through the AI's {@code via} points — and keeps
 * the first whose wires join exactly the two pins' nets and nothing else, preferring one that does not run through a
 * part's body. The check is the compiled netlist itself, not the geometry: a corner on a foreign wire or a wire over a
 * foreign pin joins that net, so the route is refused and the error names the nets it would have joined. Two nets of
 * different widths are never joined.
 */
final class ConnectRouting {

    /** What a connection did: the wires it added, the two nets it joined (one when they were joined already). */
    record Outcome(List<String> wireIds, List<Net> joinedNets, boolean alreadyConnected, List<String> warnings) {
    }

    /** One way to draw the connection: its corner points, from the first pin to the second. */
    private record Route(List<GridPoint> points) {
    }

    /** A route tried on the candidate: what it added and what it would join. */
    private record Trial(Route route, List<AddWireCommand> commands, Set<ElementId> wiresBefore,
                         boolean joinsOnlyTheTwoNets, List<Net> foreignNets, List<ElementId> crossedBodies) {
    }

    private final CircuitDocument candidate;
    private final ComponentTypeRegistry registry;
    private final NetlistCompiler compiler;
    private final Function<PinRef, PinReference> naming;

    ConnectRouting(CircuitDocument candidate, ComponentTypeRegistry registry, Function<PinRef, PinReference> naming) {
        this.candidate = candidate;
        this.registry = registry;
        this.compiler = new NetlistCompiler(registry);
        this.naming = naming;
    }

    /** Joins {@code from} and {@code to} on the candidate, or throws and leaves the candidate as it was. */
    Outcome connect(PinRef from, PinRef to, List<GridPoint> via) {
        Netlist before = compiler.compile(candidate);
        Net fromNet = before.netForPin(from).orElseThrow();
        Net toNet = before.netForPin(to).orElseThrow();
        if (fromNet.id().equals(toNet.id())) {
            return new Outcome(List.of(), List.of(fromNet), true,
                    List.of(describe(from) + " and " + describe(to) + " were connected already: no wire added"));
        }
        requireSameWidth(from, fromNet, to, toNet);
        GridPoint start = pinLocation(from);
        GridPoint end = pinLocation(to);
        Trial accepted = null;
        Trial firstRefused = null;
        for (Route route : routes(start, end, via)) {
            Trial trial = attempt(route, before, fromNet, toNet, from);
            if (trial.joinsOnlyTheTwoNets() && trial.crossedBodies().isEmpty()) {
                return outcome(trial, fromNet, toNet);
            }
            undo(trial);
            if (trial.joinsOnlyTheTwoNets() && accepted == null) {
                accepted = trial;
            } else if (!trial.joinsOnlyTheTwoNets() && firstRefused == null) {
                firstRefused = trial;
            }
        }
        if (accepted != null) {
            Trial redone = attempt(accepted.route(), before, fromNet, toNet, from);
            return outcome(redone, fromNet, toNet);
        }
        throw refusal(from, to, firstRefused);
    }

    private Outcome outcome(Trial trial, Net fromNet, Net toNet) {
        List<String> wireIds = candidate.wires().stream()
                .map(Wire::id)
                .filter(id -> !trial.wiresBefore().contains(id))
                .map(ElementId::value)
                .toList();
        List<String> warnings = new ArrayList<>();
        if (!trial.crossedBodies().isEmpty()) {
            warnings.add("the wire runs through the body of " + trial.crossedBodies().stream()
                    .map(ElementId::value).toList() + "; give via points to go round it");
        }
        return new Outcome(wireIds, List.of(fromNet, toNet), false, warnings);
    }

    private Trial attempt(Route route, Netlist before, Net fromNet, Net toNet, PinRef from) {
        Set<ElementId> wiresBefore = new HashSet<>();
        candidate.wires().forEach(wire -> wiresBefore.add(wire.id()));
        List<AddWireCommand> commands = new ArrayList<>();
        List<GridPoint> points = route.points();
        for (int index = 1; index < points.size(); index++) {
            AddWireCommand command = new AddWireCommand(new Wire(candidate.newWireId(), points.get(index - 1),
                    points.get(index)));
            command.apply(candidate);
            commands.add(command);
        }
        Netlist after = compiler.compile(candidate);
        Net joined = after.netForPin(from).orElseThrow();
        Set<PinRef> expected = new HashSet<>(fromNet.pins());
        expected.addAll(toNet.pins());
        boolean onlyTheTwo = joined.pins().equals(expected) && after.nets().size() == before.nets().size() - 1;
        List<Net> foreign = onlyTheTwo ? List.of() : foreignNets(before, joined, fromNet, toNet);
        return new Trial(route, commands, wiresBefore, onlyTheTwo, foreign, crossedBodies(route));
    }

    private void undo(Trial trial) {
        for (int index = trial.commands().size() - 1; index >= 0; index--) {
            trial.commands().get(index).revert(candidate);
        }
    }

    /** The nets of {@code before}, other than the two asked for, that the joined net swallowed. */
    private static List<Net> foreignNets(Netlist before, Net joined, Net fromNet, Net toNet) {
        List<Net> foreign = new ArrayList<>();
        for (Net net : before.nets()) {
            if (net.id().equals(fromNet.id()) || net.id().equals(toNet.id())) {
                continue;
            }
            boolean sharesPins = net.pins().stream().anyMatch(joined.pins()::contains);
            boolean sharesPoints = net.points().stream().anyMatch(joined.points()::contains);
            if (sharesPins || sharesPoints) {
                foreign.add(net);
            }
        }
        return foreign;
    }

    private AutomationException refusal(PinRef from, PinRef to, Trial refused) {
        List<List<PinReference>> wouldJoin = new ArrayList<>();
        List<List<String>> wouldJoinWires = new ArrayList<>();
        for (Net net : refused.foreignNets()) {
            if (net.pins().isEmpty()) {
                wouldJoinWires.add(net.wires().stream().map(ElementId::value).sorted().toList());
            } else {
                wouldJoin.add(references(net));
            }
        }
        Map<String, Object> recovery = new LinkedHashMap<>();
        recovery.put("wouldJoin", wouldJoin);
        recovery.put("wouldJoinWires", wouldJoinWires);
        recovery.put("triedRoute", refused.route().points().stream().map(point -> List.of(point.x(), point.y()))
                .toList());
        return new AutomationException(AutomationError.ROUTE_REJECTED, "Every route from " + describe(from) + " to "
                + describe(to) + " touches another net " + wouldJoin + "; give via points that go round it",
                recovery);
    }

    /** The net's pins as the AI names them, in a stable order. */
    List<PinReference> references(Net net) {
        return net.pins().stream()
                .sorted(Comparator.comparing((PinRef pin) -> pin.componentId().value()).thenComparingInt(PinRef::pinIndex))
                .map(naming)
                .toList();
    }

    private void requireSameWidth(PinRef from, Net fromNet, PinRef to, Net toNet) {
        Optional<Integer> fromWidth = knownWidth(fromNet);
        Optional<Integer> toWidth = knownWidth(toNet);
        if (fromWidth.isPresent() && toWidth.isPresent() && !fromWidth.equals(toWidth)) {
            throw new AutomationException(AutomationError.INVALID_PIN, describe(from) + " carries " + fromWidth.get()
                    + " bits and " + describe(to) + " carries " + toWidth.get() + " bits: they cannot be joined",
                    Map.of("fromWidth", fromWidth.get(), "toWidth", toWidth.get()));
        }
    }

    /** The width the net's pins give it; empty when only adaptive pins (tunnels) or no pins are on it. */
    private Optional<Integer> knownWidth(Net net) {
        return net.pins().stream()
                .map(this::specOf)
                .filter(spec -> !spec.isAdaptiveWidth())
                .map(PinSpec::width)
                .max(Integer::compare);
    }

    private PinSpec specOf(PinRef pin) {
        Component component = candidate.component(pin.componentId()).orElseThrow();
        return registry.typeOf(component).pin(pin.pinIndex());
    }

    private GridPoint pinLocation(PinRef pin) {
        Component component = candidate.component(pin.componentId()).orElseThrow();
        ComponentType type = registry.typeOf(component);
        return type.pinLocation(component, pin.pinIndex());
    }

    private String describe(PinRef pin) {
        PinReference reference = naming.apply(pin);
        return reference.elementId() + "." + reference.pinName();
    }

    /** The routes to try, in order of preference; with via points, only the one through them. */
    private static List<Route> routes(GridPoint start, GridPoint end, List<GridPoint> via) {
        if (!via.isEmpty()) {
            List<GridPoint> stops = new ArrayList<>();
            stops.add(start);
            stops.addAll(via);
            stops.add(end);
            List<GridPoint> corners = new ArrayList<>(List.of(start));
            for (int index = 1; index < stops.size(); index++) {
                GridPoint previous = stops.get(index - 1);
                GridPoint next = stops.get(index);
                corners.add(new GridPoint(next.x(), previous.y()));
                corners.add(next);
            }
            return List.of(simplified(corners));
        }
        int middleX = Math.floorDiv(start.x() + end.x(), 2);
        int middleY = Math.floorDiv(start.y() + end.y(), 2);
        Set<Route> routes = new LinkedHashSet<>();
        routes.add(simplified(List.of(start, new GridPoint(middleX, start.y()), new GridPoint(middleX, end.y()), end)));
        routes.add(simplified(List.of(start, new GridPoint(end.x(), start.y()), end)));
        routes.add(simplified(List.of(start, new GridPoint(start.x(), end.y()), end)));
        routes.add(simplified(List.of(start, new GridPoint(start.x(), middleY), new GridPoint(end.x(), middleY), end)));
        return List.copyOf(routes);
    }

    /** The corners without repeated points and without points in the middle of a straight run. */
    private static Route simplified(List<GridPoint> corners) {
        List<GridPoint> distinct = new ArrayList<>();
        for (GridPoint point : corners) {
            if (distinct.isEmpty() || !distinct.getLast().equals(point)) {
                distinct.add(point);
            }
        }
        List<GridPoint> result = new ArrayList<>();
        for (int index = 0; index < distinct.size(); index++) {
            boolean interior = index > 0 && index < distinct.size() - 1
                    && collinear(distinct.get(index - 1), distinct.get(index), distinct.get(index + 1));
            if (!interior) {
                result.add(distinct.get(index));
            }
        }
        return new Route(List.copyOf(result));
    }

    /** True when {@code middle} lies on the straight run from {@code first} to {@code last}, going one way. */
    private static boolean collinear(GridPoint first, GridPoint middle, GridPoint last) {
        boolean horizontal = first.y() == middle.y() && middle.y() == last.y()
                && Integer.signum(middle.x() - first.x()) == Integer.signum(last.x() - middle.x());
        boolean vertical = first.x() == middle.x() && middle.x() == last.x()
                && Integer.signum(middle.y() - first.y()) == Integer.signum(last.y() - middle.y());
        return horizontal || vertical;
    }

    /** The parts whose body (inside their box, not on its border) a segment of {@code route} runs through. */
    private List<ElementId> crossedBodies(Route route) {
        List<ElementId> crossed = new ArrayList<>();
        List<GridPoint> points = route.points();
        for (Component component : candidate.components()) {
            Bounds box = registry.typeOf(component).bounds(component);
            for (int index = 1; index < points.size(); index++) {
                if (runsThrough(points.get(index - 1), points.get(index), box)) {
                    crossed.add(component.id());
                    break;
                }
            }
        }
        return crossed;
    }

    private static boolean runsThrough(GridPoint first, GridPoint second, Bounds box) {
        if (first.y() == second.y()) {
            int low = Math.min(first.x(), second.x());
            int high = Math.max(first.x(), second.x());
            return first.y() > box.y() && first.y() < box.maximumY() && low < box.maximumX() && high > box.x();
        }
        int low = Math.min(first.y(), second.y());
        int high = Math.max(first.y(), second.y());
        return first.x() > box.x() && first.x() < box.maximumX() && low < box.maximumY() && high > box.y();
    }
}
