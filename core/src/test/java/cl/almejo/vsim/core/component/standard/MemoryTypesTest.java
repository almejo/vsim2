package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.memory.MemoryKind;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The RAM and ROM types: parameters, pins and how the engine treats a change of each parameter. */
class MemoryTypesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    /** The index of the pin called {@code name} in {@code type}. */
    static int pinIndex(ComponentType type, String name) {
        int index = pinIndexOrMinusOne(type, name);
        if (index < 0) {
            throw new AssertionError("no pin " + name + " in " + type.pins());
        }
        return index;
    }

    /** The index of the pin called {@code name} in {@code type}, or -1 when it has none. */
    static int pinIndexOrMinusOne(ComponentType type, String name) {
        for (int index = 0; index < type.pinCount(); index++) {
            if (type.pin(index).name().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    private static PinSpec pinNamed(ComponentType type, String name) {
        return type.pin(pinIndex(type, name));
    }

    private ComponentType ram(Map<String, Object> parameters) {
        return registry.require(StandardComponentTypes.RAM).specializedFor(parameters);
    }

    @Test
    void ramAndRomAreMemoriesWithTheirParameters() {
        ComponentType ram = registry.require(StandardComponentTypes.RAM);
        ComponentType rom = registry.require(StandardComponentTypes.ROM);
        assertThat(ram.category()).isEqualTo(ComponentCategory.MEMORY);
        assertThat(rom.category()).isEqualTo(ComponentCategory.MEMORY);
        assertThat(MemoryTypes.kindOf(StandardComponentTypes.RAM)).contains(MemoryKind.RAM);
        assertThat(MemoryTypes.kindOf(StandardComponentTypes.ROM)).contains(MemoryKind.ROM);
        assertThat(MemoryTypes.kindOf(StandardComponentTypes.REGISTER)).isEmpty();
        assertThat(ram.parameters()).extracting(ParameterSpec::name).containsExactly(MemoryTypes.WORDS,
                MemoryTypes.WIDTH, MemoryTypes.CONNECTION, MemoryTypes.DATA, MemoryTypes.WRITE,
                StandardParameters.EDGE.name(), MemoryTypes.SHOW_CONTENTS, "delay", MemoryTypes.CONTENTS,
                MemoryTypes.LINK);
        assertThat(rom.parameters()).extracting(ParameterSpec::name).containsExactly(MemoryTypes.WORDS,
                MemoryTypes.WIDTH, MemoryTypes.CONNECTION, MemoryTypes.SHOW_CONTENTS, "delay", MemoryTypes.CONTENTS,
                MemoryTypes.LINK);
        Map<String, Object> defaults = ram.resolveParameters(Map.of());
        assertThat(defaults).containsEntry(MemoryTypes.WORDS, "256").containsEntry(MemoryTypes.WIDTH, 8)
                .containsEntry(MemoryTypes.CONNECTION, MemoryTypes.CONNECTION_BUS)
                .containsEntry(MemoryTypes.DATA, MemoryTypes.DATA_SEPARATE)
                .containsEntry(MemoryTypes.WRITE, MemoryTypes.WRITE_CLOCK)
                .containsEntry(StandardParameters.EDGE.name(), StandardParameters.FALLING)
                .containsEntry(MemoryTypes.SHOW_CONTENTS, MemoryTypes.SHOW_CONTENTS_YES)
                .containsEntry(MemoryTypes.CONTENTS, "").containsEntry(MemoryTypes.LINK, "");
        assertThat(ram.parameters()).filteredOn(ParameterSpec::hidden).extracting(ParameterSpec::name)
                .containsExactly(MemoryTypes.CONTENTS, MemoryTypes.LINK);
        assertThat(ram.parameters()).filteredOn(ParameterSpec::displayOnly).extracting(ParameterSpec::name)
                .containsExactly(MemoryTypes.SHOW_CONTENTS, MemoryTypes.LINK);
        List<String> words = IntStream.rangeClosed(1, 24).mapToObj(bits -> String.valueOf(1 << bits)).toList();
        assertThat(ram.parameters().getFirst().choices()).isEqualTo(words);
    }

    @Test
    void theBusPinsOfADefaultRam() {
        ComponentType ram = registry.require(StandardComponentTypes.RAM);
        assertThat(ram.pins()).extracting(PinSpec::name).containsExactly("A", "D", "WE", "CS", "CLK", "Q");
        assertThat(ram.pins()).extracting(PinSpec::width).containsExactly(8, 8, 1, 1, 1, 8);
        assertThat(ram.pins()).extracting(PinSpec::direction).containsExactly(PinDirection.INPUT,
                PinDirection.INPUT, PinDirection.INPUT, PinDirection.INPUT, PinDirection.INPUT, PinDirection.OUTPUT);
        assertThat(pinNamed(ram, "A").position().x()).isZero();
        assertThat(pinNamed(ram, "Q").position().x()).isEqualTo(ram.size().width());
        for (String control : List.of("WE", "CS", "CLK")) {
            assertThat(pinNamed(ram, control).position().y()).as(control).isEqualTo(ram.size().height());
        }
    }

    @Test
    void loosePinsHaveOneBitEach() {
        ComponentType loose = ram(Map.of(MemoryTypes.CONNECTION, MemoryTypes.CONNECTION_PINS));
        List<String> expected = new ArrayList<>();
        IntStream.range(0, 8).forEach(bit -> expected.add("A" + bit));
        IntStream.range(0, 8).forEach(bit -> expected.add("D" + bit));
        expected.addAll(List.of("WE", "CS", "CLK"));
        IntStream.range(0, 8).forEach(bit -> expected.add("Q" + bit));
        assertThat(loose.pins()).extracting(PinSpec::name).containsExactlyElementsOf(expected);
        assertThat(loose.pins()).extracting(PinSpec::width).containsOnly(1);
        assertThat(loose.pins().stream().map(PinSpec::position).distinct()).hasSize(loose.pinCount());
        assertThat(loose.pins()).allSatisfy(pin -> assertThat(pin.position().x()).isBetween(0,
                loose.size().width()));
        assertThat(loose.pins()).allSatisfy(pin -> assertThat(pin.position().y()).isBetween(0,
                loose.size().height()));
    }

    @Test
    void pinsStayPutWhenTheWordsGrow() {
        ComponentType small = ram(Map.of(MemoryTypes.WORDS, "256"));
        ComponentType big = ram(Map.of(MemoryTypes.WORDS, "512"));
        for (String name : List.of("A", "D", "WE", "CS", "CLK", "Q")) {
            assertThat(pinNamed(big, name).position()).as(name).isEqualTo(pinNamed(small, name).position());
        }
        assertThat(pinNamed(big, "A").width()).isEqualTo(9);

        ComponentType looseSmall = ram(Map.of(MemoryTypes.WORDS, "256", MemoryTypes.CONNECTION,
                MemoryTypes.CONNECTION_PINS));
        ComponentType looseBig = ram(Map.of(MemoryTypes.WORDS, "512", MemoryTypes.CONNECTION,
                MemoryTypes.CONNECTION_PINS));
        for (int bit = 0; bit < 8; bit++) {
            assertThat(pinNamed(looseBig, "A" + bit).position()).isEqualTo(pinNamed(looseSmall, "A" + bit).position());
            assertThat(pinNamed(looseBig, "Q" + bit).position()).isEqualTo(pinNamed(looseSmall, "Q" + bit).position());
        }
    }

    @Test
    void aSharedDataBusIsBidirectionalWithoutQ() {
        ComponentType shared = ram(Map.of(MemoryTypes.DATA, MemoryTypes.DATA_SHARED));
        assertThat(shared.pins()).extracting(PinSpec::name).containsExactly("A", "D", "WE", "CS", "CLK");
        assertThat(pinNamed(shared, "D").direction()).isEqualTo(PinDirection.BIDIRECTIONAL);

        ComponentType sharedLoose = ram(Map.of(MemoryTypes.DATA, MemoryTypes.DATA_SHARED, MemoryTypes.CONNECTION,
                MemoryTypes.CONNECTION_PINS, MemoryTypes.WIDTH, 4, MemoryTypes.WORDS, "4"));
        assertThat(sharedLoose.pins()).extracting(PinSpec::name)
                .containsExactly("A0", "A1", "D0", "D1", "D2", "D3", "WE", "CS", "CLK");
        assertThat(pinNamed(sharedLoose, "D2").direction()).isEqualTo(PinDirection.BIDIRECTIONAL);
    }

    @Test
    void sizesOutOfRangeAreRefused() {
        ComponentType ram = registry.require(StandardComponentTypes.RAM);
        assertThatThrownBy(() -> ram.resolveParameters(Map.of(MemoryTypes.WORDS, "300")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ram.resolveParameters(Map.of(MemoryTypes.WORDS, "1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ram.resolveParameters(Map.of(MemoryTypes.WIDTH, 65)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ram.resolveParameters(Map.of(MemoryTypes.CONTENTS, "@0 XYZ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ram.resolveParameters(Map.of(MemoryTypes.WORDS, "16777216", MemoryTypes.WIDTH, 64)))
                .containsEntry(MemoryTypes.WORDS, "16777216");
        assertThat(ram.resolveParameters(Map.of(MemoryTypes.CONTENTS, "  @00 0a   0B ")))
                .as("contents are kept in their canonical text").containsEntry(MemoryTypes.CONTENTS, "@0 A B");
    }

    // --- The engine and parameter changes ---

    private final SimulationEngine engine = new SimulationEngine();

    /**
     * Two 16 x 8 RAMs "first" and "second" with A, D, WE, CLK fed by input pins (shared between them) and their
     * outputs on nets "q-first" and "q-second". CS is left unconnected (it counts as 1).
     */
    private FlatCircuit twoRams(Map<String, Object> firstExtra, Map<String, Object> secondExtra) {
        Map<String, Object> firstParameters = new HashMap<>(Map.of(MemoryTypes.WORDS, "16"));
        firstParameters.putAll(firstExtra);
        Map<String, Object> secondParameters = new HashMap<>(Map.of(MemoryTypes.WORDS, "16"));
        secondParameters.putAll(secondExtra);
        ComponentType firstType = ram(firstParameters);
        ComponentType secondType = ram(secondParameters);
        List<FlatComponent> components = new ArrayList<>(List.of(
                new FlatComponent("first", firstType, firstParameters),
                new FlatComponent("second", secondType, secondParameters)));
        List<FlatNet> nets = new ArrayList<>();
        for (String input : List.of("A", "D", "WE", "CLK")) {
            // «sin reloj» RAMs have no CLK pin: the input is then wired to whichever RAM has it (or none)
            boolean inFirst = pinIndexOrMinusOne(firstType, input) >= 0;
            boolean inSecond = pinIndexOrMinusOne(secondType, input) >= 0;
            if (!inFirst && !inSecond) {
                continue;
            }
            int width = pinNamed(inFirst ? firstType : secondType, input).width();
            Map<String, Object> source = Map.of("value", "0", InterfacePins.WIDTH, width);
            components.add(new FlatComponent(input, registry.require(InterfacePins.INPUT_PIN).specializedFor(source),
                    source));
            List<FlatPinRef> references = new ArrayList<>(List.of(new FlatPinRef(input, 0)));
            if (inFirst) {
                references.add(new FlatPinRef("first", pinIndex(firstType, input)));
            }
            if (inSecond) {
                references.add(new FlatPinRef("second", pinIndex(secondType, input)));
            }
            nets.add(new FlatNet("n-" + input, references, width, false));
        }
        nets.add(new FlatNet("q-first", List.of(new FlatPinRef("first", pinIndex(firstType, "Q"))), 8, false));
        nets.add(new FlatNet("q-second", List.of(new FlatPinRef("second", pinIndex(secondType, "Q"))), 8, false));
        return new FlatCircuit(components, nets);
    }

    /** Writes {@code value} at address 3 of both RAMs and leaves A at 3. */
    private void writeThreeInBoth(long value) {
        engine.setInput("A", Value.of(4, 3));
        engine.setInput("D", Value.of(8, value));
        engine.setInput("WE", Value.ONE);
        engine.setInput("CLK", Value.ONE);
        engine.advance(10);
        engine.setInput("CLK", Value.ZERO);
        engine.advance(10);
        engine.setInput("WE", Value.ZERO);
        engine.advance(10);
    }

    @Test
    void showContentsAndLinkKeepTheRunningMemory() {
        engine.update(twoRams(Map.of(), Map.of()));
        writeThreeInBoth(0x5A);
        engine.update(twoRams(Map.of(MemoryTypes.SHOW_CONTENTS, MemoryTypes.SHOW_CONTENTS_NO,
                MemoryTypes.LINK, "programa.hex"), Map.of()));
        engine.advance(10);
        assertThat(engine.snapshot().netValue("q-first")).isEqualTo(Value.of(8, 0x5A));
    }

    @Test
    void aContentsChangeRecreatesOnlyThatMemory() {
        engine.update(twoRams(Map.of(), Map.of()));
        writeThreeInBoth(0x5A);
        engine.update(twoRams(Map.of(MemoryTypes.CONTENTS, "@3 11"), Map.of()));
        engine.advance(10);
        assertThat(engine.snapshot().netValue("q-first")).as("new initial contents").isEqualTo(Value.of(8, 0x11));
        assertThat(engine.snapshot().netValue("q-second")).as("untouched").isEqualTo(Value.of(8, 0x5A));
    }

    /** Sets A = 3, D = {@code value}, WE = 1 and lets it settle (the clock is left alone: «sin reloj» RAMs have none). */
    private void prepareWriteAtThree(long value) {
        engine.setInput("A", Value.of(4, 3));
        engine.setInput("D", Value.of(8, value));
        engine.setInput("WE", Value.ONE);
        engine.advance(10);
    }

    @Test
    void aRisingRamWritesOnTheZeroToOneStepOnly() {
        Map<String, Object> rising = Map.of(StandardParameters.EDGE.name(), StandardParameters.RISING);
        engine.update(twoRams(rising, Map.of()));
        prepareWriteAtThree(0x5A);
        engine.setInput("CLK", Value.ZERO);
        engine.advance(10);
        engine.setInput("CLK", Value.ONE);
        engine.advance(10);
        assertThat(engine.snapshot().netValue("q-first")).as("rising RAM wrote on 0 to 1")
                .isEqualTo(Value.of(8, 0x5A));
        assertThat(engine.snapshot().netValue("q-second")).as("falling RAM waits for 1 to 0")
                .isNotEqualTo(Value.of(8, 0x5A));
        engine.setInput("A", Value.of(4, 4));
        engine.setInput("D", Value.of(8, 0x33));
        engine.setInput("CLK", Value.ZERO);
        engine.advance(10);
        engine.setInput("A", Value.of(4, 3));
        engine.advance(10);
        assertThat(engine.snapshot().netValue("q-first")).as("the falling step wrote nothing in the rising RAM")
                .isEqualTo(Value.of(8, 0x5A));
        assertThat(engine.snapshot().netValue("q-second")).as("the falling RAM wrote 0x33 at 4, not at 3")
                .isNotEqualTo(Value.of(8, 0x5A));
    }

    @Test
    void theDefaultRamStillWritesOnTheFallingEdge() {
        engine.update(twoRams(Map.of(), Map.of()));
        writeThreeInBoth(0x5A);
        assertThat(engine.snapshot().netValue("q-first")).isEqualTo(Value.of(8, 0x5A));
        assertThat(engine.snapshot().netValue("q-second")).isEqualTo(Value.of(8, 0x5A));
    }

    @Test
    void levelWritingIgnoresTheEdge() {
        Map<String, Object> levelRising = Map.of(MemoryTypes.WRITE, MemoryTypes.WRITE_LEVEL,
                StandardParameters.EDGE.name(), StandardParameters.RISING);
        Map<String, Object> level = Map.of(MemoryTypes.WRITE, MemoryTypes.WRITE_LEVEL);
        engine.update(twoRams(levelRising, level));
        prepareWriteAtThree(0x5A);
        assertThat(engine.snapshot().netValue("q-first")).isEqualTo(Value.of(8, 0x5A));
        assertThat(engine.snapshot().netValue("q-second")).isEqualTo(Value.of(8, 0x5A));
    }

    @Test
    void theEdgeIsVisibleOnlyForClockedWriting() {
        ComponentType ram = registry.require(StandardComponentTypes.RAM);
        ParameterSpec edge = ram.parameters().stream()
                .filter(spec -> spec.name().equals(StandardParameters.EDGE.name())).findFirst().orElseThrow();
        assertThat(edge.visibility()).hasValueSatisfying(condition -> {
            assertThat(condition.parameter()).isEqualTo(MemoryTypes.WRITE);
            assertThat(condition.value()).isEqualTo(MemoryTypes.WRITE_CLOCK);
        });
        assertThat(edge.defaultValue()).isEqualTo(StandardParameters.FALLING);
    }

    @Test
    void aRamSavedWithoutAnEdgeLoadsFalling() {
        assertThat(ram(Map.of(MemoryTypes.WORDS, "16")).resolveParameters(Map.of(MemoryTypes.WORDS, "16")))
                .containsEntry(StandardParameters.EDGE.name(), StandardParameters.FALLING);
    }
}
