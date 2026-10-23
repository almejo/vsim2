package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.command.TidyWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.examples.ExampleCircuits;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.netlist.ProbeResolver;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableEvaluator;
import cl.almejo.vsim.core.truthtable.TruthTableScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tidying never changes what a circuit means (spec M6.2 §2.3 and §3): over every bundled example, every committed
 * example file and hundreds of seeded messy circuits, with every option on. Each check works on the document as the
 * {@link TidyWiresCommand} leaves it, independently of the {@link Connectivity} guard inside {@link WireTidy}.
 */
class TidyEquivalenceTest {

    private static final TidyOptions ALL = new TidyOptions(true, true, true);
    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();
    private static final int SEEDS = 240;
    /** How long a circuit without a usable truth table is simulated, and how often its outputs are sampled. */
    private static final long SIMULATED_TIME = 1_200;
    private static final long SAMPLE_PERIOD = 20;
    private static final long SWITCH_PERIOD = 100;

    /**
     * The committed example files, never a directory listing (people keep their own untracked circuits under
     * examples/): the test fixtures {@link ExampleCircuits} generates (fixtures/m3), plus a fixed list.
     */
    private static final List<String> OTHER_COMMITTED_FILES = List.of(
            "fixtures/m4/cortocircuitos.vsim", "vsim1/4-bit-adder.json", "vsim1/circuit.json", "vsim1/one-gate.json");

    private record Example(String name, Loader loader) {

        CircuitDocument open() throws CircuitFormatException {
            return loader.open();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private interface Loader {
        CircuitDocument open() throws CircuitFormatException;
    }

    static List<Example> examples() {
        List<Example> examples = new ArrayList<>();
        for (String id : BundledExamples.ids()) {
            examples.add(new Example("bundled/" + id, () -> BundledExamples.open(id, REGISTRY)));
        }
        List<String> files = new ArrayList<>();
        ExampleCircuits.builders().keySet().stream().sorted().forEach(name -> files.add("fixtures/m3/" + name + ".vsim"));
        files.addAll(OTHER_COMMITTED_FILES);
        for (String file : files) {
            examples.add(new Example(file, () -> new CircuitReader(REGISTRY).read(Path.of("src/test/resources", file))));
        }
        return examples;
    }

    static Stream<Integer> seeds() {
        return IntStream.range(0, SEEDS).boxed();
    }

    // --- the examples as they are ------------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("examples")
    void tidyingAnExampleKeepsItEquivalent(Example example) throws Exception {
        CircuitDocument document = example.open();
        checkTidy(document, allWires(document), example.name());
    }

    @Test
    void theExampleListHasEveryBundledExampleAndTheCommittedFiles() {
        assertThat(examples()).hasSize(BundledExamples.ids().size() + ExampleCircuits.builders().size()
                + OTHER_COMMITTED_FILES.size());
    }

    // --- messy circuits ----------------------------------------------------------------------------------------

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void tidyingAMessyCircuitKeepsItEquivalentAndRemovesTheMess(int seed) throws Exception {
        List<Example> examples = examples();
        Example example = examples.get(seed % examples.size());
        CircuitDocument document = cleanCopyOf(example);
        Random random = new Random(seed);
        MessyCircuits.Mess mess = MessyCircuits.mess(document, registryFor(document), random);
        String context = "seed " + seed + ", " + example.name() + ": " + mess;

        boolean partial = seed % 4 == 3;
        Set<ElementId> scope = partial ? randomHalf(document, random) : allWires(document);
        TidyPlan plan = checkTidy(document, scope, context);
        if (partial) {
            return;
        }

        History history = new History(document);
        if (!plan.isEmpty()) {
            history.execute(new TidyWiresCommand(plan));
        }
        assertThat(plan.detoursFlattened()).as("clear detours flattened; %s", context)
                .isGreaterThanOrEqualTo(mess.clearDetours());
        for (ElementId joiningWire : mess.joiningWires()) {
            assertThat(document.wire(joiningWire)).as("the wire joining two crossing wires stays; %s", context)
                    .isPresent();
        }
        assertNoMessLeft(document, Set.copyOf(mess.joiningWires()), context);
    }

    @Test
    void theGeneratorMakesEveryKindOfMess() throws Exception {
        int[] totals = new int[9];
        List<Example> examples = examples();
        for (int seed = 0; seed < 60; seed++) {
            CircuitDocument document = cleanCopyOf(examples.get(seed % examples.size()));
            MessyCircuits.Mess mess = MessyCircuits.mess(document, registryFor(document), new Random(seed));
            totals[0] += mess.stubs();
            totals[1] += mess.cuts();
            totals[2] += mess.duplicates();
            totals[3] += mess.clearDetours();
            totals[4] += mess.blockedDetours();
            totals[5] += mess.floatingWires();
            totals[6] += mess.probedWires();
            totals[7] += mess.joiningCuts();
            totals[8] += mess.joiningWires().size();
        }
        assertThat(IntStream.of(totals).min().orElseThrow())
                .as("fewest of stubs, cuts, duplicates, clear U, blocked U, floating, probed, joining cuts, "
                        + "joining overlaps: %s", Arrays.toString(totals))
                .isGreaterThanOrEqualTo(5);
    }

    /** The example with a tidy already applied, so all the mess found later is the generator's. */
    private static CircuitDocument cleanCopyOf(Example example) throws CircuitFormatException {
        CircuitDocument document = example.open();
        TidyPlan plan = WireTidy.plan(document, registryFor(document), allWires(document), ALL);
        if (!plan.isEmpty()) {
            new History(document).execute(new TidyWiresCommand(plan));
        }
        return document;
    }

    private static Set<ElementId> randomHalf(CircuitDocument document, Random random) {
        Set<ElementId> scope = new HashSet<>();
        document.wires().stream().filter(wire -> random.nextBoolean()).forEach(wire -> scope.add(wire.id()));
        return scope;
    }

    /**
     * After a full tidy: every wire end joins something (a pin or another wire) unless its net is a watched floating
     * one or the wire is a joining overlap, every floating net is watched, no two wires overlap on a line (joining
     * overlaps aside), and no two collinear wires just meet end to end with nothing else there.
     */
    private static void assertNoMessLeft(CircuitDocument document, Set<ElementId> joiningWires, String context) {
        ComponentTypeRegistry registry = registryFor(document);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        WireGraph graph = WireGraph.of(document.wires(), document, registry);
        Set<ElementId> watched = new HashSet<>();
        for (Probe probe : document.probes()) {
            if (probe.target() instanceof ProbeTarget.WireTarget target) {
                watched.add(target.wireId());
            }
        }
        List<Wire> wires = document.wires();
        for (Wire wire : wires) {
            Net net = netlist.netForWire(wire.id()).orElseThrow();
            boolean watchedFloating = net.pins().isEmpty() && net.wires().stream().anyMatch(watched::contains);
            if (net.pins().isEmpty()) {
                assertThat(watchedFloating).as("floating %s is watched; %s", MessyCircuits.describe(wire), context)
                        .isTrue();
            }
            if (!watchedFloating && !joiningWires.contains(wire.id())) {
                Set<GridPoint> joints = graph.connectionPointsOn(wire);
                assertThat(joints).as("both ends of %s join something; %s", MessyCircuits.describe(wire), context)
                        .contains(wire.start(), wire.end());
            }
            for (Wire other : wires) {
                if (other != wire && !joiningWires.contains(wire.id()) && !joiningWires.contains(other.id())) {
                    assertThat(wire.overlapsCollinear(other)).as("%s overlaps %s; %s", MessyCircuits.describe(wire),
                            MessyCircuits.describe(other), context).isFalse();
                }
            }
            for (GridPoint end : List.of(wire.start(), wire.end())) {
                List<Wire> through = graph.wiresThrough(end);
                boolean mergeable = through.size() == 2 && graph.pinsAt(end).isEmpty()
                        && through.get(0).isHorizontal() == through.get(1).isHorizontal()
                        && through.stream().allMatch(each -> each.start().equals(end) || each.end().equals(end));
                assertThat(mergeable).as("%s still meets a collinear wire end to end at %s; %s",
                        MessyCircuits.describe(wire), end, context).isFalse();
            }
        }
    }

    // --- the checks every tidy must pass -----------------------------------------------------------------------

    /**
     * Plans a tidy of {@code scope}, applies it as a command and checks: only scope wires change, the same pins are
     * joined, every probe watches the same thing, the same behaviour (truth table or simulated outputs); with the
     * whole circuit in scope a second tidy finds nothing; undo restores elements, ids, order and probes exactly.
     * The document is left tidied (redone).
     */
    private static TidyPlan checkTidy(CircuitDocument document, Set<ElementId> scope, String context)
            throws Exception {
        ComponentTypeRegistry registry = registryFor(document);
        List<DocumentElement> elementsBefore = document.elements();
        Map<ElementId, Long> ordersBefore = ordersOf(document);
        List<Probe> probesBefore = document.probes();
        Set<Set<PinRef>> partitionBefore = pinPartition(document, registry);
        Map<String, Object> probesWatchBefore = probeView(document, registry);
        Object behaviourBefore = behaviour(document, registry);
        Set<GridPoint> wiredPointsBefore = wiredPoints(document);

        TidyPlan plan = WireTidy.plan(document, registry, scope, ALL);
        for (Wire removed : plan.removed()) {
            assertThat(scope).as("only wires in scope change; %s", context).contains(removed.id());
        }
        History history = new History(document);
        if (plan.isEmpty()) {
            assertThat(document.elements()).isEqualTo(elementsBefore);
        } else {
            history.execute(new TidyWiresCommand(plan));
        }

        assertThat(pinPartition(document, registry)).as("pin partition; %s", context).isEqualTo(partitionBefore);
        assertThat(probeView(document, registry)).as("probes; %s", context).isEqualTo(probesWatchBefore);
        for (Probe probe : document.probes()) {
            if (probe.target() instanceof ProbeTarget.WireTarget target) {
                assertThat(document.wire(target.wireId())).as("probe %s wire exists; %s", probe.id(), context)
                        .isPresent();
            }
        }
        assertThat(behaviour(document, registry)).as("behaviour; %s", context).isEqualTo(behaviourBefore);
        assertNewPointsTouchNothing(document, registry, wiredPointsBefore, context);
        if (scope.containsAll(allWires(elementsBefore))) {
            assertThat(WireTidy.plan(document, registry, allWires(document), ALL).isEmpty())
                    .as("a second tidy finds nothing; %s", context).isTrue();
        }

        if (!plan.isEmpty()) {
            List<DocumentElement> elementsAfter = document.elements();
            history.undo();
            assertThat(document.elements()).as("undo elements; %s", context).isEqualTo(elementsBefore);
            assertThat(ordersOf(document)).as("undo order; %s", context).isEqualTo(ordersBefore);
            assertThat(document.probes()).as("undo probes; %s", context).isEqualTo(probesBefore);
            history.redo();
            assertThat(document.elements()).as("redo; %s", context).isEqualTo(elementsAfter);
        }
        return plan;
    }

    /**
     * Wires reach new grid points only when a U detour is flattened, and there they must touch nothing (spec 2.2.4):
     * no pin, no component body, no other wire. This is what keeps a detour blocked by a crossing wire. The one
     * exception is a corner: when one flattening moves a wire another flattening then uses as a leg, the moved
     * middle meets that leg at a point no wire covered before.
     */
    private static void assertNewPointsTouchNothing(CircuitDocument document, ComponentTypeRegistry registry,
                                                    Set<GridPoint> wiredPointsBefore, String context) {
        WireGraph graph = WireGraph.of(document.wires(), document, registry);
        List<Bounds> bodies = document.components().stream()
                .map(component -> registry.typeOf(component).bounds(component))
                .toList();
        for (GridPoint point : wiredPoints(document)) {
            if (wiredPointsBefore.contains(point)) {
                continue;
            }
            assertThat(graph.pinsAt(point)).as("no pin at new point %s; %s", point, context).isEmpty();
            List<Wire> through = graph.wiresThrough(point);
            boolean corner = through.size() == 2 && through.get(0).isHorizontal() != through.get(1).isHorizontal()
                    && through.stream().allMatch(wire -> wire.start().equals(point) || wire.end().equals(point));
            assertThat(through.size() == 1 || corner)
                    .as("new point %s has one wire or is the corner of a moved U: %s; %s", point, through, context)
                    .isTrue();
            assertThat(bodies).as("no body at new point %s; %s", point, context)
                    .noneMatch(body -> body.contains(point));
        }
    }

    private static Set<GridPoint> wiredPoints(CircuitDocument document) {
        Set<GridPoint> points = new HashSet<>();
        document.wires().forEach(wire -> points.addAll(MessyCircuits.pointsOf(wire)));
        return points;
    }

    private static ComponentTypeRegistry registryFor(CircuitDocument document) {
        return REGISTRY.extendedWith(SubcircuitTypes.resolverFor(document));
    }

    private static Set<ElementId> allWires(CircuitDocument document) {
        return allWires(document.elements());
    }

    private static Set<ElementId> allWires(List<DocumentElement> elements) {
        Set<ElementId> ids = new HashSet<>();
        elements.stream().filter(Wire.class::isInstance).forEach(element -> ids.add(element.id()));
        return ids;
    }

    private static Map<ElementId, Long> ordersOf(CircuitDocument document) {
        Map<ElementId, Long> orders = new HashMap<>();
        document.elements().forEach(element -> orders.put(element.id(), document.orderOf(element.id())));
        return orders;
    }

    /** The top-level pins grouped by net (nets without pins left out). */
    private static Set<Set<PinRef>> pinPartition(CircuitDocument document, ComponentTypeRegistry registry) {
        Set<Set<PinRef>> partition = new HashSet<>();
        for (Net net : new NetlistCompiler(registry).compile(document).nets()) {
            if (!net.pins().isEmpty()) {
                partition.add(Set.copyOf(net.pins()));
            }
        }
        return partition;
    }

    /**
     * What each probe watches: the pins of its net, or, for a net without pins, the probes that share it (as a
     * sorted list), or nothing.
     */
    private static Map<String, Object> probeView(CircuitDocument document, ComponentTypeRegistry registry) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        Map<String, String> netOfProbe = new HashMap<>();
        Map<String, List<String>> probesOfNet = new HashMap<>();
        for (Probe probe : document.probes()) {
            ProbeResolver.netOf(probe.target(), document, netlist).ifPresent(netId -> {
                netOfProbe.put(probe.id(), netId);
                probesOfNet.computeIfAbsent(netId, key -> new ArrayList<>()).add(probe.id());
            });
        }
        Map<String, Object> view = new HashMap<>();
        for (Probe probe : document.probes()) {
            String netId = netOfProbe.get(probe.id());
            if (netId == null) {
                view.put(probe.id(), "nothing");
                continue;
            }
            Net net = netlist.net(new NetId(netId)).orElseThrow();
            view.put(probe.id(), net.pins().isEmpty()
                    ? probesOfNet.get(netId).stream().sorted().toList()
                    : Set.copyOf(net.pins()));
        }
        return view;
    }

    /**
     * The circuit's truth table when it has one computed at once (combinational, at most 10 input bits); otherwise
     * every component's visible state sampled while the circuit runs, its switches clicked one after another.
     */
    private static Object behaviour(CircuitDocument document, ComponentTypeRegistry registry) {
        Preparation preparation = TruthTableScope.prepare(document, Set.of(), registry);
        if (preparation instanceof Preparation.Ready ready && !ready.needsConfirmation()) {
            TruthTable table = TruthTableEvaluator.evaluate(ready, percent -> { }, () -> false).orElseThrow();
            return table;
        }
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationEngine engine = new SimulationEngine();
        engine.update(new CircuitFlattener(registry).flatten(document, netlist));
        List<String> switches = document.components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.SWITCH))
                .map(component -> component.id().value())
                .sorted()
                .toList();
        List<Map<String, VisibleState>> samples = new ArrayList<>();
        int clicks = 0;
        for (long time = SAMPLE_PERIOD; time <= SIMULATED_TIME; time += SAMPLE_PERIOD) {
            engine.runUntil(time);
            if (!switches.isEmpty() && time % SWITCH_PERIOD == 0) {
                engine.interact(switches.get(clicks++ % switches.size()));
            }
            SimulationSnapshot snapshot = engine.snapshot();
            Map<String, VisibleState> sample = new LinkedHashMap<>();
            for (Component component : document.components()) {
                sample.put(component.id().value(), snapshot.componentState(component.id().value()));
            }
            samples.add(sample);
        }
        return samples;
    }
}
