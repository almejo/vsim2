package cl.almejo.vsim.core;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Loads circuits saved by VSim 1 and checks that they simulate correctly end to end. */
class FullAdderIntegrationTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final NetlistCompiler compiler = new NetlistCompiler(registry);
    private final CircuitFlattener flattener = new CircuitFlattener(registry);
    private final SimulationEngine engine = new SimulationEngine();
    private CircuitDocument document;
    private Netlist netlist;

    private void load(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/vsim1/" + resource)) {
            document = new CircuitReader(registry).read(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        synchronize();
    }

    private void synchronize() {
        netlist = compiler.compile(document);
        engine.update(flattener.flatten(document, netlist));
    }

    private Value output(Component component, int pin) {
        String netId = netlist.netForPin(new PinRef(component.id(), pin)).orElseThrow().id().value();
        return engine.snapshot().netValue(netId);
    }

    private Component labeled(String label) {
        return document.components().stream().filter(component -> component.label().equals(label)).findFirst().orElseThrow();
    }

    @Test
    void oneGateExampleComputesXor() throws Exception {
        load("one-gate.json");
        engine.advance(10);
        Component xor = document.components().stream().filter(component -> component.typeId().equals("xor")).findFirst().orElseThrow();
        assertThat(output(xor, 2)).isEqualTo(Value.ONE);
        assertThat(engine.snapshot().problems()).filteredOn(problem -> !(problem instanceof ConnectivityProblem))
                .isEmpty();
    }

    @ParameterizedTest(name = "a={0} b={1} c={2}")
    @CsvSource({"0,0,0", "0,0,1", "0,1,0", "0,1,1", "1,0,0", "1,0,1", "1,1,0", "1,1,1"})
    void fullAdderFromVsimAddsCorrectly(int a, int b, int carryIn) throws Exception {
        load("4-bit-adder.json");
        setSwitch("a0", a);
        setSwitch("b0", b);
        setSwitch("c", carryIn);
        synchronize();
        engine.advance(20);

        Component sumGate = document.components().stream()
                .filter(component -> component.typeId().equals("xor"))
                .max(Comparator.comparingInt(component -> component.position().x()))
                .orElseThrow();
        Component carryGate = document.components().stream()
                .filter(component -> component.typeId().equals("or"))
                .findFirst().orElseThrow();
        int total = a + b + carryIn;
        assertThat(output(sumGate, 2)).isEqualTo(Value.of(total % 2 == 1));
        assertThat(output(carryGate, 2)).isEqualTo(Value.of(total >= 2));
        assertThat(engine.snapshot().problems()).filteredOn(problem -> !(problem instanceof ConnectivityProblem))
                .isEmpty();
    }

    private void setSwitch(String label, int value) {
        document.replace(labeled(label).withParameters(Map.of("value", String.valueOf(value))));
    }

    @Test
    void vsimExampleCircuitImportsAndSimulatesWithoutElectricalProblems() throws Exception {
        load("circuit.json");
        assertThat(document.components()).hasSize(21);
        engine.advance(5000);
        SimulationSnapshot snapshot = engine.snapshot();
        assertThat(snapshot.problems()).filteredOn(problem -> !(problem instanceof ConnectivityProblem)).isEmpty();
        assertThat(snapshot.time()).isEqualTo(5000);
        for (Component component : document.components()) {
            int pinCount = registry.require(component.typeId()).pinCount();
            for (int pinIndex = 0; pinIndex < pinCount; pinIndex++) {
                String netId = netlist.netForPin(new PinRef(component.id(), pinIndex))
                        .map(net -> net.id().value())
                        .orElse("p:" + component.id().value() + "#" + pinIndex);
                assertThat(snapshot.netValues()).as("net of %s pin %d", component.id(), pinIndex).containsKey(netId);
            }
        }
    }
}
