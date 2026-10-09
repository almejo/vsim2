package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.BehaviorFactory;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentInstanceMatchingTest {

    @Test
    void aTypeChangedUnderTheSameIdRecreatesTheInstance() {
        AtomicInteger created = new AtomicInteger();
        BehaviorFactory factory = parameters -> {
            created.incrementAndGet();
            return (context, pinIndex) -> {
            };
        };
        ComponentType small = new ComponentType("box", ComponentCategory.BASIC, new GridSize(2, 2),
                List.of(PinSpec.input("in", 0, 1)), List.of(), factory);
        ComponentType wide = new ComponentType("box", ComponentCategory.BASIC, new GridSize(4, 2),
                List.of(PinSpec.input("in", 0, 1), PinSpec.input("other", 0, 2)), List.of(), factory);
        SimulationEngine engine = new SimulationEngine();

        engine.update(new FlatCircuit(List.of(new FlatComponent("c1", small, Map.of())), List.of()));
        engine.update(new FlatCircuit(List.of(new FlatComponent("c1", small, Map.of())), List.of()));
        assertThat(created).hasValue(1);

        engine.update(new FlatCircuit(List.of(new FlatComponent("c1", wide, Map.of())), List.of()));
        assertThat(created).hasValue(2);
    }
}
