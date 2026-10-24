package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Checks geometry at every instance level, independently of signal values and simulation state. */
public final class ConnectivityChecks {

    // Match the flattener's safety bound; valid definitions form an acyclic graph.
    private static final int MAXIMUM_DEPTH = 32;

    private ConnectivityChecks() {
    }

    /** A fresh immutable diagnostic list; definitions are compiled once and reported separately per instance. */
    public static List<ConnectivityProblem> problems(CircuitDocument document, ComponentTypeRegistry registry) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        Check check = new Check(registry);
        check.visit(document, "", 0);
        return List.copyOf(check.problems);
    }

    private record Level(CircuitDocument document, Netlist netlist, Netlist asDrawn) {
    }

    /** A single traversal's compiled definitions and collected instance-specific diagnostics. */
    private static final class Check {
        private final ComponentTypeRegistry registry;
        private final NetlistCompiler compiler;
        private final Map<ComponentDefinition, Level> bodies = new IdentityHashMap<>();
        private final List<ConnectivityProblem> problems = new ArrayList<>();

        Check(ComponentTypeRegistry registry) {
            this.registry = registry;
            this.compiler = new NetlistCompiler(registry);
        }

        private void visit(CircuitDocument document, String prefix, int depth) {
            visit(compileLevel(document), prefix, depth);
        }

        private void visit(Level level, String prefix, int depth) {
            if (depth > MAXIMUM_DEPTH) {
                throw new IllegalStateException("sub-circuits nest more than " + MAXIMUM_DEPTH + " levels");
            }
            Set<GridPoint> pinPositions = new HashSet<>();
            for (Component component : level.document().components()) {
                ComponentType type = registry.typeOf(component);
                checkPins(level, component, type, prefix, pinPositions);
                SubcircuitTypes.definitionOf(type).ifPresent(definition -> visit(
                        bodies.computeIfAbsent(definition, this::compileBody),
                        prefix + component.id().value() + InstancePath.SEPARATOR, depth + 1));
            }
            // The circuit itself is the user's work in progress: its loose wire ends are not faults, only those
            // inside a component's body are.
            if (!prefix.isEmpty()) {
                checkWireEnds(level, prefix, pinPositions);
            }
        }

        private Level compileBody(ComponentDefinition definition) {
            CircuitDocument document = new CircuitDocument();
            definition.body().elements().forEach(document::add);
            return compileLevel(document);
        }

        private Level compileLevel(CircuitDocument document) {
            Netlist netlist = compiler.compile(document);
            boolean hasTunnels = document.components().stream()
                    .anyMatch(component -> Tunnels.isTunnel(component.typeId()));
            return new Level(document, netlist, hasTunnels ? compiler.compileAsDrawn(document) : netlist);
        }

        private void checkPins(Level level, Component component, ComponentType type, String prefix,
                               Set<GridPoint> pinPositions) {
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                GridPoint position = type.pinLocation(component, pinIndex);
                pinPositions.add(position);
                // Open terminals on the canvas and a definition's interface are not faults inside its body.
                if (prefix.isEmpty() || InterfacePins.isInterface(type.id())) {
                    continue;
                }
                PinRef reference = new PinRef(component.id(), pinIndex);
                Optional<Net> net = level.netlist().netForPin(reference);
                Optional<Net> physical = Tunnels.isTunnel(type.id()) ? level.asDrawn().netForPin(reference) : net;
                // Interface pins and tunnel links are real peers in the local netlist. Merely touching a wire is not.
                // A tunnel's own pin must reach something on its drawn side, not just a namesake tunnel.
                if (physical.isEmpty() || physical.get().pins().size() < 2) {
                    String netId = net.map(found -> found.id().value()).orElse(component.id().value());
                    problems.add(new ConnectivityProblem(prefix + netId, prefix + component.id().value(),
                            pinIndex, position));
                }
            }
        }

        private void checkWireEnds(Level level, String prefix, Set<GridPoint> pinPositions) {
            Set<GridPoint> checked = new LinkedHashSet<>();
            Map<GridPoint, Set<GridPoint>> directions = wireDirections(level.document().wires());
            for (Wire wire : level.document().wires()) {
                for (GridPoint endpoint : List.of(wire.start(), wire.end())) {
                    if (!checked.add(endpoint) || pinPositions.contains(endpoint)
                            || directions.get(endpoint).size() > 1) {
                        continue;
                    }
                    String netId = level.netlist().netForWire(wire.id())
                            .map(net -> net.id().value()).orElse(wire.id().value());
                    problems.add(new ConnectivityProblem(prefix + netId, prefix + wire.id().value(), -1, endpoint));
                }
            }
        }

        /** Distinct rays distinguish a real corner/junction from overlapping wires ending on the same side. */
        private Map<GridPoint, Set<GridPoint>> wireDirections(List<Wire> wires) {
            Map<GridPoint, Set<GridPoint>> directions = new HashMap<>();
            Map<Integer, TreeMap<Integer, GridPoint>> rows = new HashMap<>();
            Map<Integer, TreeMap<Integer, GridPoint>> columns = new HashMap<>();
            for (Wire wire : wires) {
                for (GridPoint point : List.of(wire.start(), wire.end())) {
                    directions.computeIfAbsent(point, unused -> new HashSet<>());
                    rows.computeIfAbsent(point.y(), unused -> new TreeMap<>()).put(point.x(), point);
                    columns.computeIfAbsent(point.x(), unused -> new TreeMap<>()).put(point.y(), point);
                }
            }
            for (Wire wire : wires) {
                Map<Integer, GridPoint> contained = wire.isHorizontal()
                        ? rows.get(wire.start().y()).subMap(wire.start().x(), true, wire.end().x(), true)
                        : columns.get(wire.start().x()).subMap(wire.start().y(), true, wire.end().y(), true);
                for (GridPoint point : contained.values()) {
                    addDirection(directions.get(point), point, wire.start());
                    addDirection(directions.get(point), point, wire.end());
                }
            }
            return directions;
        }

        private void addDirection(Set<GridPoint> directions, GridPoint from, GridPoint to) {
            if (!from.equals(to)) {
                directions.add(new GridPoint(Integer.compare(to.x(), from.x()), Integer.compare(to.y(), from.y())));
            }
        }
    }
}
