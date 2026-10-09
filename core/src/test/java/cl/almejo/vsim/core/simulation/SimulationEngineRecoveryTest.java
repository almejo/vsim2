package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationEngineRecoveryTest {

    private static final String FRAGILE = "test-fragile";

    private final AtomicBoolean failing = new AtomicBoolean(true);
    private final AtomicInteger initializations = new AtomicInteger();
    private final ComponentTypeRegistry registry = fragileRegistry();

    private ComponentTypeRegistry fragileRegistry() {
        ComponentTypeRegistry result = new ComponentTypeRegistry();
        result.register(new ComponentType(FRAGILE, ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(PinSpec.output("out", 2, 1)), List.of(),
                parameters -> new Behavior() {
                    @Override
                    public void initialize(BehaviorContext context) {
                        initializations.incrementAndGet();
                        if (failing.get()) {
                            throw new IllegalStateException("broken on purpose");
                        }
                        context.drive(0, Value.ONE, 0);
                    }

                    @Override
                    public void inputChanged(BehaviorContext context, int pinIndex) {
                    }
                }));
        return result;
    }

    @Test
    void componentWhoseInitializationFailedIsInitializedAgainOnTheNextUpdate() {
        CircuitDocument document = new CircuitDocument();
        ElementId id = document.newComponentId();
        document.add(new Component(id, FRAGILE, GridPoint.ORIGIN, Rotation.R0, Map.of(), ""));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        FlatCircuit circuit = new CircuitFlattener(registry).flatten(document, netlist);
        SimulationEngine engine = new SimulationEngine();

        assertThatThrownBy(() -> engine.update(circuit)).isInstanceOf(IllegalStateException.class);
        failing.set(false);
        engine.update(circuit);

        String netId = netlist.netForPin(new PinRef(id, 0)).orElseThrow().id().value();
        assertThat(initializations).hasValue(2);
        assertThat(engine.snapshot().netValue(netId)).isEqualTo(Value.ONE);
    }
}
