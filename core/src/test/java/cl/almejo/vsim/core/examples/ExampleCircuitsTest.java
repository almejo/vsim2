package cl.almejo.vsim.core.examples;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The committed files in examples/m3 must be what {@link ExampleCircuits} builds, and must behave as intended. */
class ExampleCircuitsTest {

    private static final Path EXAMPLES_DIRECTORY = Path.of("../examples/m3");
    private static final String SHORT_CIRCUIT = "short-circuit";

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);

    static Stream<String> exampleNames() {
        return ExampleCircuits.builders().keySet().stream();
    }

    @Test
    void everyCommittedFileHasABuilderAndViceVersa() throws IOException {
        try (Stream<Path> files = Files.list(EXAMPLES_DIRECTORY)) {
            Set<String> committed = new HashSet<>();
            files.filter(path -> path.toString().endsWith(".vsim"))
                    .forEach(path -> committed.add(path.getFileName().toString().replaceAll("\\.vsim$", "")));
            assertThat(committed).containsExactlyInAnyOrderElementsOf(ExampleCircuits.builders().keySet());
        }
    }

    @ParameterizedTest
    @MethodSource("exampleNames")
    void committedFileEqualsWhatTheGeneratorBuilds(String name) throws Exception {
        CircuitDocument committed = new CircuitReader(registry).read(EXAMPLES_DIRECTORY.resolve(name + ".vsim"));
        CircuitDocument generated = ExampleCircuits.builders().get(name).get().document();
        assertThat(format.write(committed)).isEqualTo(format.write(generated));
    }

    @ParameterizedTest
    @MethodSource("exampleNames")
    void netsJoinExactlyTheIntendedPins(String name) {
        CircuitBuilder builder = ExampleCircuits.builders().get(name).get();
        Netlist netlist = new NetlistCompiler(registry).compile(builder.document());

        Map<PinRef, Set<PinRef>> expected = new HashMap<>();
        for (Component component : builder.document().components()) {
            for (int pinIndex = 0; pinIndex < registry.require(component.typeId()).pinCount(); pinIndex++) {
                PinRef pin = new PinRef(component.id(), pinIndex);
                expected.put(pin, new HashSet<>(Set.of(pin)));
            }
        }
        for (List<PinRef> link : builder.links()) {
            Set<PinRef> merged = new HashSet<>(expected.get(link.get(0)));
            merged.addAll(expected.get(link.get(1)));
            merged.forEach(pin -> expected.put(pin, merged));
        }

        Map<String, Set<PinRef>> actual = new HashMap<>();
        for (Net net : netlist.nets()) {
            assertThat(net.pins()).as("net %s has pins (no dangling wires)", net.id()).isNotEmpty();
            assertThat(net.widthMismatch()).isFalse();
            actual.put(net.id().value(), new HashSet<>(net.pins()));
        }
        assertThat(new HashSet<>(actual.values())).isEqualTo(new HashSet<>(expected.values()));
    }

    @ParameterizedTest
    @MethodSource("exampleNames")
    void simulatesWithoutFailure(String name) throws Exception {
        CircuitDocument document = new CircuitReader(registry).read(EXAMPLES_DIRECTORY.resolve(name + ".vsim"));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationEngine engine = new SimulationEngine();
        engine.update(new CircuitFlattener(registry).flatten(document, netlist));
        engine.advance(400);
        SimulationSnapshot snapshot = engine.snapshot();

        assertThat(snapshot.failure()).isEmpty();
        assertThat(snapshot.time()).isEqualTo(400);
        if (name.equals(SHORT_CIRCUIT)) {
            assertThat(snapshot.shortCircuits()).hasSizeGreaterThanOrEqualTo(2);
        } else {
            assertThat(snapshot.problems()).isEmpty();
        }
    }

    @Test
    void halfAdderComputesSumAndCarry() throws Exception {
        CircuitDocument document = new CircuitReader(registry).read(EXAMPLES_DIRECTORY.resolve("labels.vsim"));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationEngine engine = new SimulationEngine();
        engine.update(new CircuitFlattener(registry).flatten(document, netlist));
        engine.advance(20);
        assertThat(ledValue(document, netlist, engine, "Suma")).isEqualTo(Value.ONE);
        assertThat(ledValue(document, netlist, engine, "Acarreo")).isEqualTo(Value.ZERO);
    }

    private static Value ledValue(CircuitDocument document, Netlist netlist, SimulationEngine engine, String label) {
        Component led = document.components().stream()
                .filter(component -> component.label().equals(label)).findFirst().orElseThrow();
        String netId = netlist.netForPin(new PinRef(led.id(), 0)).orElseThrow().id().value();
        return engine.snapshot().netValue(netId);
    }
}
