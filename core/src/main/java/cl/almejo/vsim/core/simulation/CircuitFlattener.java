package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.tunnel.TunnelChecks;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a document and its netlist into a {@link FlatCircuit}. Sub-circuit instances are expanded recursively: child
 * {@code c2} of instance {@code c5} becomes {@code c5/c2} ({@link InstancePath}), the instance itself disappears, and
 * each instance pin joins the outer net with the net of the matching interface pin inside. Interface pins inside an
 * instance become passive probes as wide as they are ({@link #interfaceProbe}), so their value stays visible under their own id. Merged nets keep
 * the id closest to the top level; the others become {@link FlatNet#aliases()}. Tunnels are left out (the netlist
 * already joined them by name); their problems, found per body, go in {@link FlatCircuit#tunnelProblems()}.
 */
public final class CircuitFlattener {

    public static final String INTERFACE_PROBE_ID = "interface-probe";
    public static final ComponentType INTERFACE_PROBE = new ComponentType(INTERFACE_PROBE_ID,
            ComponentCategory.INTERFACE, new GridSize(1, 1),
            List.of(new PinSpec("pin", GridPoint.ORIGIN, PinDirection.INPUT, 1)), List.of(),
            parameters -> StandardComponentTypes.newValueProbe());
    private static final Map<Integer, ComponentType> WIDE_PROBES = new ConcurrentHashMap<>();
    private static final int MAXIMUM_DEPTH = 32;

    private final ComponentTypeRegistry registry;
    private final NetlistCompiler compiler;

    public CircuitFlattener(ComponentTypeRegistry registry) {
        this.registry = registry;
        this.compiler = new NetlistCompiler(registry);
    }

    /**
     * The passive stand-in for an interface pin of {@code width} bits inside an instance: {@link #INTERFACE_PROBE} for
     * one bit, otherwise one cached type per width with a single input pin of that width.
     */
    public static ComponentType interfaceProbe(int width) {
        if (width == 1) {
            return INTERFACE_PROBE;
        }
        return WIDE_PROBES.computeIfAbsent(width, key -> new ComponentType(INTERFACE_PROBE_ID,
                ComponentCategory.INTERFACE, new GridSize(1, 1),
                List.of(new PinSpec("pin", GridPoint.ORIGIN, PinDirection.INPUT, key)), List.of(),
                parameters -> StandardComponentTypes.newValueProbe()));
    }

    public FlatCircuit flatten(CircuitDocument document, Netlist netlist) {
        Expansion expansion = new Expansion();
        expansion.addLevel("", document, netlist, 0);
        return new FlatCircuit(document.revision(), expansion.components, expansion.flatNets(),
                expansion.tunnelProblems);
    }

    private record Body(CircuitDocument document, Netlist netlist) {
    }

    private Body compileBody(ComponentDefinition definition) {
        CircuitDocument body = new CircuitDocument();
        definition.body().elements().forEach(body::add);
        return new Body(body, compiler.compile(body));
    }

    private static final class NetBuilder {
        final int width;
        final boolean widthMismatch;
        final List<FlatPinRef> pins = new ArrayList<>();

        NetBuilder(int width, boolean widthMismatch) {
            this.width = width;
            this.widthMismatch = widthMismatch;
        }
    }

    /** State of one flatten call; bodies are compiled once per call because nested definitions may have changed. */
    private final class Expansion {
        final List<FlatComponent> components = new ArrayList<>();
        final Map<String, NetBuilder> nets = new LinkedHashMap<>();
        final Map<String, String> parents = new HashMap<>();
        final Map<ComponentDefinition, Body> bodies = new IdentityHashMap<>();
        final List<TunnelProblem> tunnelProblems = new ArrayList<>();

        void addLevel(String prefix, CircuitDocument level, Netlist levelNetlist, int depth) {
            if (depth > MAXIMUM_DEPTH) {
                throw new IllegalStateException("sub-circuits nest more than " + MAXIMUM_DEPTH + " levels");
            }
            for (Net net : levelNetlist.nets()) {
                nets.put(prefix + net.id().value(), new NetBuilder(net.width(), net.widthMismatch()));
            }
            TunnelChecks.problems(level, levelNetlist, registry)
                    .forEach(problem -> tunnelProblems.add(problem.nestedIn(prefix)));
            for (Component component : level.components()) {
                String flatId = prefix + component.id().value();
                ComponentType type = registry.typeOf(component);
                if (Tunnels.isTunnel(type.id())) {
                    // tunnels only join nets, which the netlist already did
                    continue;
                }
                Optional<ComponentDefinition> definition = SubcircuitTypes.definitionOf(type);
                if (definition.isPresent()) {
                    expand(flatId, component, definition.get(), levelNetlist, prefix, depth);
                } else if (!prefix.isEmpty() && InterfacePins.isInterface(type.id())) {
                    addComponent(new FlatComponent(flatId, interfaceProbe(type.pin(0).width()), Map.of()), component,
                            levelNetlist, prefix);
                } else {
                    addComponent(new FlatComponent(flatId, type, component.parameters()), component, levelNetlist,
                            prefix);
                }
            }
        }

        private void addComponent(FlatComponent flat, Component component, Netlist levelNetlist, String prefix) {
            components.add(flat);
            for (int pinIndex = 0; pinIndex < flat.type().pinCount(); pinIndex++) {
                FlatPinRef reference = new FlatPinRef(flat.id(), pinIndex);
                levelNetlist.netForPin(new PinRef(component.id(), pinIndex))
                        .ifPresent(net -> nets.get(prefix + net.id().value()).pins.add(reference));
            }
        }

        private void expand(String instanceId, Component instance, ComponentDefinition definition,
                            Netlist levelNetlist, String prefix, int depth) {
            Body body = bodies.computeIfAbsent(definition, CircuitFlattener.this::compileBody);
            String innerPrefix = instanceId + InstancePath.SEPARATOR;
            addLevel(innerPrefix, body.document(), body.netlist(), depth + 1);
            for (int pinIndex = 0; pinIndex < definition.pinout().size(); pinIndex++) {
                Optional<Net> outer = levelNetlist.netForPin(new PinRef(instance.id(), pinIndex));
                Optional<Net> inner = body.netlist()
                        .netForPin(new PinRef(definition.pinout().get(pinIndex).source(), 0));
                if (outer.isPresent() && inner.isPresent()) {
                    union(prefix + outer.get().id().value(), innerPrefix + inner.get().id().value());
                }
            }
        }

        private String find(String netId) {
            String current = netId;
            while (parents.containsKey(current)) {
                current = parents.get(current);
            }
            return current;
        }

        private void union(String first, String second) {
            String firstRoot = find(first);
            String secondRoot = find(second);
            if (firstRoot.equals(secondRoot)) {
                return;
            }
            if (closerToTop(secondRoot, firstRoot)) {
                parents.put(firstRoot, secondRoot);
            } else {
                parents.put(secondRoot, firstRoot);
            }
        }

        private static boolean closerToTop(String candidate, String other) {
            int candidateDepth = InstancePath.depth(candidate);
            int otherDepth = InstancePath.depth(other);
            return candidateDepth != otherDepth ? candidateDepth < otherDepth : candidate.compareTo(other) < 0;
        }

        List<FlatNet> flatNets() {
            Map<String, List<String>> members = new LinkedHashMap<>();
            for (String netId : nets.keySet()) {
                members.computeIfAbsent(find(netId), root -> new ArrayList<>()).add(netId);
            }
            List<FlatNet> result = new ArrayList<>();
            members.forEach((root, ids) -> {
                NetBuilder rootNet = nets.get(root);
                List<FlatPinRef> pins = new ArrayList<>();
                List<String> aliases = new ArrayList<>();
                boolean mismatch = false;
                for (String id : ids) {
                    NetBuilder member = nets.get(id);
                    pins.addAll(member.pins);
                    mismatch |= member.widthMismatch || member.width != rootNet.width;
                    if (!id.equals(root)) {
                        aliases.add(id);
                    }
                }
                pins.sort(Comparator.comparing(FlatPinRef::componentId).thenComparingInt(FlatPinRef::pinIndex));
                Collections.sort(aliases);
                result.add(new FlatNet(root, pins, rootNet.width, mismatch, aliases));
            });
            return result;
        }
    }
}
