package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.examples.ExampleCircuits;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fast {@link ConnectivityChecker} the tidy run uses must agree with {@link Connectivity#of}, which compiles the
 * netlist with {@link NetlistCompiler}: on the seeded messy circuits, and on variations of them with wires taken out
 * and probes retargeted (so that the signatures compared are not all the same).
 */
class ConnectivityCheckerTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();
    private static final int SEEDS = 80;
    private static final int VARIATIONS = 6;

    /** The bundled examples ({@code bundled/<id>}) and the committed m3 example files ({@code m3/<name>.vsim}). */
    private static List<String> examples() {
        List<String> examples = new ArrayList<>();
        BundledExamples.ids().forEach(id -> examples.add("bundled/" + id));
        ExampleCircuits.builders().keySet().stream().sorted().forEach(name -> examples.add("m3/" + name + ".vsim"));
        return examples;
    }

    private static CircuitDocument open(String example) throws CircuitFormatException {
        if (example.startsWith("bundled/")) {
            return BundledExamples.open(example.substring("bundled/".length()), REGISTRY);
        }
        return new CircuitReader(REGISTRY).read(Path.of("../examples", example));
    }

    static Stream<Integer> seeds() {
        return IntStream.range(0, SEEDS).boxed();
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void theCheckerAgreesWithTheNetlistCompilerOnMessyCircuits(int seed) throws Exception {
        List<String> examples = examples();
        String example = examples.get(seed % examples.size());
        CircuitDocument document = open(example);
        ComponentTypeRegistry registry = REGISTRY.extendedWith(SubcircuitTypes.resolverFor(document));
        Random random = new Random(seed);
        MessyCircuits.Mess mess = MessyCircuits.mess(document, registry, random);
        String context = "seed " + seed + ", " + example + ": " + mess;
        ConnectivityChecker checker = new ConnectivityChecker(document, WireGraph.pinLocations(document, registry));

        List<Wire> wires = document.wires();
        assertSame(checker, wires, Map.of(), document, registry, context);
        for (int variation = 0; variation < VARIATIONS; variation++) {
            List<Wire> fewer = new ArrayList<>(wires.stream().filter(wire -> random.nextInt(5) != 0).toList());
            assertSame(checker, fewer, randomRetargets(document, fewer, random), document, registry,
                    context + ", variation " + variation);
        }
    }

    @Test
    void theVariationsReallyChangeTheConnectivity() throws Exception {
        int different = 0;
        List<String> examples = examples();
        for (int seed = 0; seed < 20; seed++) {
            CircuitDocument document = open(examples.get(seed % examples.size()));
            ComponentTypeRegistry registry = REGISTRY.extendedWith(SubcircuitTypes.resolverFor(document));
            Random random = new Random(seed);
            MessyCircuits.mess(document, registry, random);
            Connectivity whole = Connectivity.of(document.wires(), document, registry);
            List<Wire> fewer = document.wires().stream().filter(wire -> random.nextInt(5) != 0).toList();
            if (!whole.equals(Connectivity.of(fewer, document, registry))) {
                different++;
            }
        }
        assertThat(different).isGreaterThanOrEqualTo(10);
    }

    private static void assertSame(ConnectivityChecker checker, List<Wire> wires, Map<ElementId, ElementId> retargets,
                                   CircuitDocument document, ComponentTypeRegistry registry, String context) {
        assertThat(checker.of(wires, retargets)).as("connectivity; %s", context)
                .isEqualTo(Connectivity.of(wires, document, registry, retargets));

        CircuitDocument scratch = new CircuitDocument();
        document.components().forEach(scratch::add);
        wires.forEach(scratch::add);
        Set<ConnectivityChecker.WireNet> expected = new HashSet<>();
        for (Net net : new NetlistCompiler(registry).compile(scratch).nets()) {
            if (!net.wires().isEmpty()) {
                expected.add(new ConnectivityChecker.WireNet(net.wires(), net.pins()));
            }
        }
        assertThat(new HashSet<>(checker.nets(wires))).as("nets with wires; %s", context).isEqualTo(expected);
    }

    /** Some probes on wires moved to a random remaining wire, and a missing wire mapped to one that exists. */
    private static Map<ElementId, ElementId> randomRetargets(CircuitDocument document, List<Wire> wires,
                                                             Random random) {
        Map<ElementId, ElementId> retargets = new HashMap<>();
        if (wires.isEmpty()) {
            return retargets;
        }
        for (Probe probe : document.probes()) {
            if (probe.target() instanceof ProbeTarget.WireTarget target && random.nextBoolean()) {
                retargets.put(target.wireId(), wires.get(random.nextInt(wires.size())).id());
            }
        }
        return retargets;
    }
}
