package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.value.Value;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** One example circuit on its own engine, with some switches set by label before it starts (example tests). */
public final class ExampleSimulation {

    private final CircuitDocument document;
    private final SimulationEngine engine = new SimulationEngine();

    /** The builder's document (each builder call makes a new one, so setting switches here changes nothing else). */
    public ExampleSimulation(CircuitBuilder builder, Map<String, String> switchValues) {
        this(builder.document(), switchValues);
    }

    public ExampleSimulation(CircuitDocument document, Map<String, String> switchValues) {
        this.document = document;
        switchValues.forEach((label, value) -> {
            Component switchPart = component(label, StandardComponentTypes.SWITCH);
            Map<String, Object> parameters = new HashMap<>(switchPart.parameters());
            parameters.put("value", value);
            document.replace(switchPart.withParameters(parameters));
        });
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        registry.addResolver(SubcircuitTypes.resolverFor(document));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        engine.update(new CircuitFlattener(registry).flatten(document, netlist));
    }

    public SimulationEngine engine() {
        return engine;
    }

    /** Runs one simulation time after another until nothing is pending (circuits without a clock). */
    public ExampleSimulation settle() {
        for (int times = 0; engine.nextEventTime().isPresent(); times++) {
            assertThat(times).as("settles").isLessThan(10_000);
            engine.runUntil(engine.nextEventTime().getAsLong());
        }
        return this;
    }

    public ExampleSimulation runUntil(long time) {
        engine.runUntil(time);
        return this;
    }

    /** A click on the switch {@code label}, applied at the current time. */
    public ExampleSimulation toggle(String label) {
        engine.interact(component(label, StandardComponentTypes.SWITCH).id().value());
        return this;
    }

    /** Makes the Switch {@code label} (any width) hold {@code number} by clicking the bits that differ, now. */
    public ExampleSimulation setSwitch(String label, long number) {
        Component switchPart = component(label, StandardComponentTypes.SWITCH);
        String id = switchPart.id().value();
        Value current = ((VisibleState.SwitchState) engine.snapshot().componentState(id)).value();
        for (int bit = 0; bit < current.width(); bit++) {
            if (((current.bits() >> bit) & 1) != ((number >> bit) & 1)) {
                engine.interactBit(id, bit);
            }
        }
        return this;
    }

    /** Drives the input pin {@code label} with {@code value} at the current time (a value typed into its box). */
    public ExampleSimulation setInput(String label, Value value) {
        engine.setInput(component(label, StandardComponentTypes.INPUT_PIN).id().value(), value);
        return this;
    }

    /** A click on the input pin {@code label}: all zeros become all ones and back. */
    public ExampleSimulation togglePin(String label) {
        engine.interact(component(label, StandardComponentTypes.INPUT_PIN).id().value());
        return this;
    }

    /** The value the output pin {@code label} shows. */
    public Value outputPin(String label) {
        VisibleState state = engine.snapshot()
                .componentState(component(label, StandardComponentTypes.OUTPUT_PIN).id().value());
        return ((VisibleState.LedState) state).value();
    }

    public Value led(String label) {
        VisibleState state = engine.snapshot()
                .componentState(component(label, StandardComponentTypes.LED).id().value());
        return ((VisibleState.LedState) state).value();
    }

    public List<Value> leds(String... labels) {
        return Stream.of(labels).map(this::led).toList();
    }

    /** The number on the LEDs {@code prefix}0 … {@code prefix}(bits-1), each fully defined. */
    public int number(String prefix, int bits) {
        int number = 0;
        for (int bit = 0; bit < bits; bit++) {
            Value value = led(prefix + bit);
            assertThat(value.isFullyDefined()).as("%s%d is defined", prefix, bit).isTrue();
            number |= (int) value.bits() << bit;
        }
        return number;
    }

    /** What the {@code index}-th 7-segment display (single or double, left to right) shows. */
    public Value display(int index) {
        Component display = document.components().stream()
                .filter(component -> component.typeId().startsWith("seven-segments-display"))
                .sorted(Comparator.comparingInt((Component component) -> component.position().x())
                        .thenComparingInt(component -> component.position().y()))
                .toList().get(index);
        return ((VisibleState.SevenSegmentsState) engine.snapshot().componentState(display.id().value())).value();
    }

    /** The component of type {@code typeId} labeled {@code label} (a switch and an LED may share a label). */
    public Component component(String label, String typeId) {
        return document.components().stream()
                .filter(component -> component.typeId().equals(typeId) && component.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + typeId + " labeled " + label));
    }
}
