package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.BehaviorFactory;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M7 fix round (I1): a change to a display-only parameter («Mostrar como») keeps the component's behaviour, so a
 * register keeps its contents and an input pin its typed value.
 */
class DisplayOnlyParameterTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final SimulationEngine engine = new SimulationEngine();

    private FlatComponent component(String id, String typeId, Map<String, Object> parameters) {
        ComponentType type = registry.require(typeId).specializedFor(parameters);
        return new FlatComponent(id, type, parameters);
    }

    /** A 4-bit register fed by input pin "d" and clocked by "clk"; Q is net "nq", D net "nd". */
    private FlatCircuit registerCircuit(String registerFormat, String pinFormat) {
        return new FlatCircuit(List.of(
                component("reg", StandardComponentTypes.REGISTER,
                        Map.of("width", 4, "delay", 1, ConstantValue.FORMAT, registerFormat)),
                component("d", InterfacePins.INPUT_PIN,
                        Map.of("value", "0", InterfacePins.WIDTH, 4, ConstantValue.FORMAT, pinFormat)),
                component("clk", InterfacePins.INPUT_PIN, Map.of("value", "0", InterfacePins.WIDTH, 1))),
                List.of(new FlatNet("nd", List.of(new FlatPinRef("reg", 0), new FlatPinRef("d", 0)), 4, false),
                        new FlatNet("nclk", List.of(new FlatPinRef("reg", 3), new FlatPinRef("clk", 0)), 1, false),
                        new FlatNet("nq", List.of(new FlatPinRef("reg", 4)), 4, false)));
    }

    @Test
    void changingOnlyTheFormatKeepsARegistersContentsAndAPinsTypedValue() {
        engine.update(registerCircuit(ConstantValue.AUTO, ConstantValue.AUTO));
        engine.setInput("d", Value.of(4, 5));
        engine.setInput("clk", Value.ONE);
        engine.setInput("clk", Value.ZERO);
        engine.advance(10);
        assertThat(engine.snapshot().netValue("nq")).isEqualTo(Value.of(4, 5));

        engine.update(registerCircuit(ConstantValue.HEX, ConstantValue.SIGNED));
        engine.advance(10);

        assertThat(engine.snapshot().netValue("nq")).isEqualTo(Value.of(4, 5));
        assertThat(engine.snapshot().netValue("nd")).isEqualTo(Value.of(4, 5));
    }

    @Test
    void aConstantAndAnOutputPinStillWorkAfterAFormatChange() {
        FlatCircuit before = constantCircuit(ConstantValue.AUTO);
        engine.update(before);
        engine.advance(10);
        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.of(8, 0x2A));

        engine.update(constantCircuit(ConstantValue.DECIMAL));
        engine.advance(10);

        assertThat(engine.snapshot().netValue("n")).isEqualTo(Value.of(8, 0x2A));
    }

    private FlatCircuit constantCircuit(String format) {
        return new FlatCircuit(List.of(
                component("k", StandardComponentTypes.CONSTANT,
                        Map.of("width", 8, "value", "0x2A", ConstantValue.FORMAT, format)),
                component("out", InterfacePins.OUTPUT_PIN, Map.of(InterfacePins.WIDTH, 8, ConstantValue.FORMAT, format))),
                List.of(new FlatNet("n", List.of(new FlatPinRef("k", 0), new FlatPinRef("out", 0)), 8, false)));
    }

    @Test
    void theFormatIsDisplayOnlyOnEveryTypeThatHasIt() {
        assertThat(ConstantValue.FORMAT_PARAMETER.displayOnly()).isTrue();
        for (String typeId : List.of(StandardComponentTypes.CONSTANT, InterfacePins.INPUT_PIN, InterfacePins.OUTPUT_PIN,
                StandardComponentTypes.REGISTER, StandardComponentTypes.COUNTER)) {
            assertThat(registry.require(typeId).parameters())
                    .filteredOn(parameter -> parameter.name().equals(ConstantValue.FORMAT))
                    .singleElement().matches(ParameterSpec::displayOnly, typeId);
        }
    }

    @Test
    void onlyADisplayOnlyChangeKeepsTheInstance() {
        AtomicInteger created = new AtomicInteger();
        BehaviorFactory factory = parameters -> {
            created.incrementAndGet();
            return (context, pinIndex) -> {
            };
        };
        ComponentType type = new ComponentType("box", ComponentCategory.GATES, new GridSize(2, 2),
                List.of(PinSpec.input("in", 0, 1)),
                List.of(ParameterSpec.integer("size", 1, 1, 9),
                        ParameterSpec.choice("look", "plain", List.of("plain", "fancy")).asDisplayOnly()),
                factory);

        engine.update(new FlatCircuit(List.of(new FlatComponent("c1", type, Map.of("look", "plain"))), List.of()));
        engine.update(new FlatCircuit(List.of(new FlatComponent("c1", type, Map.of("look", "fancy"))), List.of()));
        assertThat(created).hasValue(1);

        engine.update(new FlatCircuit(List.of(new FlatComponent("c1", type, Map.of("size", 2))), List.of()));
        assertThat(created).hasValue(2);
    }
}
