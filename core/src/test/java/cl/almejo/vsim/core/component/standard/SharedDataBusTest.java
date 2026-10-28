package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Datos: un solo bus D» (spec §4.2): the RAM drives its bidirectional D with the word at A while CS = 1 and WE = 0
 * and releases it otherwise, so a student's driver on the same net can write through it. The released memory is never
 * blamed for a short circuit and its own drive never makes it oscillate.
 */
class SharedDataBusTest {

    private static final long SETTLE = 100;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final SimulationEngine engine = new SimulationEngine();

    /** A 16 x 8 shared-bus RAM; A, WE, CS and CLK from input pins, and a «student» driver (all Z) on the bus. */
    private void build(String write, String contents) {
        Map<String, Object> parameters = new HashMap<>(Map.of(MemoryTypes.WORDS, "16", MemoryTypes.DATA,
                MemoryTypes.DATA_SHARED, MemoryTypes.WRITE, write, MemoryTypes.CONTENTS, contents));
        ComponentType ram = registry.require(StandardComponentTypes.RAM).specializedFor(parameters);
        List<FlatComponent> components = new ArrayList<>(List.of(new FlatComponent("ram", ram, parameters)));
        List<FlatNet> nets = new ArrayList<>();
        for (String input : List.of("A", "WE", "CS", "CLK")) {
            if (MemoryTypesTest.pinIndexOrMinusOne(ram, input) < 0) {
                continue;
            }
            int width = ram.pin(MemoryTypesTest.pinIndex(ram, input)).width();
            components.add(source(input, width, input.equals("CS") ? "1" : "0"));
            nets.add(new FlatNet("n-" + input, List.of(new FlatPinRef(input, 0),
                    new FlatPinRef("ram", MemoryTypesTest.pinIndex(ram, input))), width, false));
        }
        components.add(source("student", 8, "Z"));
        nets.add(new FlatNet("bus", List.of(new FlatPinRef("ram", MemoryTypesTest.pinIndex(ram, "D")),
                new FlatPinRef("student", 0)), 8, false));
        engine.update(new FlatCircuit(components, nets));
        settle();
    }

    private FlatComponent source(String id, int width, String value) {
        Map<String, Object> parameters = Map.of("value", value, InterfacePins.WIDTH, width);
        return new FlatComponent(id, registry.require(InterfacePins.INPUT_PIN).specializedFor(parameters), parameters);
    }

    private void set(String input, Value value) {
        engine.setInput(input, value);
    }

    private void settle() {
        engine.advance(SETTLE);
    }

    private Value bus() {
        return engine.snapshot().netValue("bus");
    }

    private void assertQuiet() {
        assertThat(engine.snapshot().shortCircuits()).isEmpty();
        assertThat(engine.nextEventTime()).as("nothing keeps ringing").isEmpty();
    }

    @Test
    void readingDrivesTheBus() {
        build(MemoryTypes.WRITE_CLOCK, "@0 12 34");
        assertThat(bus()).isEqualTo(Value.of(8, 0x12));
        set("A", Value.of(4, 1));
        settle();
        assertThat(bus()).isEqualTo(Value.of(8, 0x34));
        assertQuiet();
    }

    @Test
    void csAtZeroReleasesTheBus() {
        build(MemoryTypes.WRITE_CLOCK, "@0 12");
        set("CS", Value.ZERO);
        settle();
        assertThat(bus()).isEqualTo(Value.highImpedance(8));
        assertQuiet();
    }

    @Test
    void aStudentDriverWritesThroughTheSameNetOnTheClockEdge() {
        build(MemoryTypes.WRITE_CLOCK, "@0 12");
        set("WE", Value.ONE);
        settle();
        assertThat(bus()).as("released while WE = 1").isEqualTo(Value.highImpedance(8));
        set("student", Value.of(8, 0x42));
        settle();
        assertQuiet();
        set("CLK", Value.ONE);
        settle();
        set("CLK", Value.ZERO);
        settle();
        set("student", Value.highImpedance(8));
        set("WE", Value.ZERO);
        settle();
        assertThat(bus()).as("the memory drives what was written").isEqualTo(Value.of(8, 0x42));
        assertQuiet();
    }

    @Test
    void levelWritingThroughTheBusStoresTheStudentsValue() {
        build(MemoryTypes.WRITE_LEVEL, "@3 99");
        set("A", Value.of(4, 3));
        settle();
        assertThat(bus()).isEqualTo(Value.of(8, 0x99));
        set("WE", Value.ONE);
        set("student", Value.of(8, 0x07));
        settle();
        assertQuiet();
        // Like a real SRAM, WE goes back to 0 before the data leaves the bus (releasing first would store Z).
        set("WE", Value.ZERO);
        set("student", Value.highImpedance(8));
        settle();
        assertThat(bus()).isEqualTo(Value.of(8, 0x07));
        assertQuiet();
    }

    @Test
    void rapidAddressChangesSettleOnTheLastAddress() {
        build(MemoryTypes.WRITE_CLOCK, "@0 1 2 3 4 5 6 7 8");
        for (int address = 0; address < 8; address++) {
            set("A", Value.of(4, address));
            set("CS", Value.of(address % 2 == 0));
        }
        set("CS", Value.ONE);
        settle();
        assertThat(bus()).isEqualTo(Value.of(8, 8));
        assertQuiet();
    }
}
