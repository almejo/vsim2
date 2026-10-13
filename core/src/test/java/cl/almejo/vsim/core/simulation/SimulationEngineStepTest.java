package cl.almejo.vsim.core.simulation;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** «Avanzar» (M6.1-B): one simulation time per call, with the nets that changed. */
class SimulationEngineStepTest {

    private static final String ZERO_DELAY_BUFFER = "test-zero-delay-buffer";
    private static final String RINGING = "test-ringing";

    private final ComponentTypeRegistry registry = TestComponentTypes.registry();
    private final SimulationEngine engine = new SimulationEngine();

    private FlatComponent component(String id, String typeId, Map<String, Object> parameters) {
        return new FlatComponent(id, registry.require(typeId), parameters);
    }

    private static FlatNet net(String id, FlatPinRef... pins) {
        return new FlatNet(id, List.of(pins), 1, false);
    }

    private static FlatPinRef pin(String componentId, int pinIndex) {
        return new FlatPinRef(componentId, pinIndex);
    }

    /** Source s (0) → inverter n1 (delay 1) → inverter n2 (delay 1): nets a, b and c. */
    private FlatCircuit inverterChain() {
        return new FlatCircuit(List.of(component("s", TestComponentTypes.SOURCE, Map.of("value", "0")),
                component("n1", TestComponentTypes.INVERTER, Map.of("delay", 1)),
                component("n2", TestComponentTypes.INVERTER, Map.of("delay", 1))),
                List.of(net("a", pin("s", 0), pin("n1", 0)), net("b", pin("n1", 1), pin("n2", 0)),
                        net("c", pin("n2", 1))));
    }

    /** A buffer whose output follows its input with no delay. */
    private void registerZeroDelayBuffer() {
        registry.register(new ComponentType(ZERO_DELAY_BUFFER, ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(PinSpec.input("in", 0, 1), PinSpec.output("out", 2, 1)), List.of(),
                parameters -> new Behavior() {
                    @Override
                    public void initialize(BehaviorContext context) {
                        inputChanged(context, 0);
                    }

                    @Override
                    public void inputChanged(BehaviorContext context, int pinIndex) {
                        context.drive(1, context.input(0), 0);
                    }
                }));
    }

    /** Output wired to its own input: at t = 5 it starts inverting itself with no delay, forever. */
    private void registerRinging() {
        registry.register(new ComponentType(RINGING, ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(PinSpec.input("in", 0, 1), PinSpec.output("out", 2, 1)), List.of(),
                parameters -> new Behavior() {
                    @Override
                    public void initialize(BehaviorContext context) {
                        context.schedule(5, () -> context.drive(1, Value.ONE, 0));
                    }

                    @Override
                    public void inputChanged(BehaviorContext context, int pinIndex) {
                        context.drive(1, context.input(0).equals(Value.ONE) ? Value.ZERO : Value.ONE, 0);
                    }
                }));
    }

    @Test
    void withNothingPendingAnAdvanceIsIdleAndTimeStays() {
        engine.update(FlatCircuit.EMPTY);
        engine.advance(4);

        StepResult result = engine.advanceToNextEvent();

        assertThat(result).isEqualTo(StepResult.idle(4));
        assertThat(result.isIdle()).isTrue();
        assertThat(engine.now()).isEqualTo(4);
    }

    @Test
    void aToggleThenThreeAdvancesWalkTheChainOneGateDelayAtATime() {
        engine.update(inverterChain());
        engine.advance(5);
        assertThat(engine.nextEventTime()).isEmpty();

        StepResult toggle = engine.interactTracked("s");
        assertThat(toggle).isEqualTo(new StepResult(5, 5, Set.of("a"), OptionalLong.of(6)));
        assertThat(toggle.isIdle()).isFalse();

        assertThat(engine.advanceToNextEvent()).isEqualTo(new StepResult(5, 6, Set.of("b"), OptionalLong.of(7)));
        assertThat(engine.snapshot().netValue("b")).isEqualTo(Value.ZERO);
        assertThat(engine.advanceToNextEvent()).isEqualTo(new StepResult(6, 7, Set.of("c"), OptionalLong.empty()));
        assertThat(engine.snapshot().netValue("c")).isEqualTo(Value.ONE);
        assertThat(engine.advanceToNextEvent().isIdle()).isTrue();
        assertThat(engine.now()).isEqualTo(7);
    }

    @Test
    void zeroDelayEffectsAtTheNextTimeRunInTheSameAdvance() {
        registerZeroDelayBuffer();
        engine.update(new FlatCircuit(List.of(component("s", TestComponentTypes.SOURCE, Map.of("value", "0")),
                component("n1", TestComponentTypes.INVERTER, Map.of("delay", 1)),
                component("z1", ZERO_DELAY_BUFFER, Map.of()), component("z2", ZERO_DELAY_BUFFER, Map.of())),
                List.of(net("a", pin("s", 0), pin("n1", 0)), net("b", pin("n1", 1), pin("z1", 0)),
                        net("c", pin("z1", 1), pin("z2", 0)), net("d", pin("z2", 1)))));
        engine.advance(5);
        engine.interactTracked("s");

        StepResult result = engine.advanceToNextEvent();

        assertThat(result).isEqualTo(new StepResult(5, 6, Set.of("b", "c", "d"), OptionalLong.empty()));
        assertThat(engine.snapshot().netValue("d")).isEqualTo(Value.ZERO);
    }

    @Test
    void aNetJoinedThroughATemplateReportsItsTopLevelAliases() {
        CircuitDocument templated = new CircuitDocument();
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        standard.addResolver(SubcircuitTypes.resolverFor(templated));
        Component input = standard.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 1), Rotation.R0, Map.of(), "a");
        Component output = standard.require(InterfacePins.OUTPUT_PIN).newComponent(new ElementId("c3"),
                new GridPoint(6, 1), Rotation.R0, Map.of(), "y");
        Wire through = new Wire(new ElementId("w2"), new GridPoint(2, 2), new GridPoint(6, 2));
        templated.putDefinition(new ComponentDefinition("through", "Puente", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id()),
                        new DefinitionPin(PinSpec.output("y", 4, 2), output.id())),
                new CircuitFragment(List.of(input, output), List.of(through), List.of())));
        ElementId switchId = new ElementId("c1");
        ElementId ledId = new ElementId("c3");
        templated.add(standard.require(StandardComponentTypes.SWITCH).newComponent(switchId, new GridPoint(0, 0),
                Rotation.R0, Map.of(), ""));
        templated.add(standard.require(SubcircuitTypes.typeIdOf("through")).newComponent(new ElementId("c2"),
                new GridPoint(4, -1), Rotation.R0, Map.of(), ""));
        templated.add(standard.require(StandardComponentTypes.LED).newComponent(ledId, new GridPoint(7, -2),
                Rotation.R0, Map.of(), ""));
        Netlist netlist = new NetlistCompiler(standard).compile(templated);
        String switchNet = netlist.netForPin(new PinRef(switchId, 0)).orElseThrow().id().value();
        String ledNet = netlist.netForPin(new PinRef(ledId, 0)).orElseThrow().id().value();
        assertThat(ledNet).isNotEqualTo(switchNet);
        engine.update(new CircuitFlattener(standard).flatten(templated, netlist));
        engine.advance(5);

        StepResult toggle = engine.interactTracked(switchId.value());

        assertThat(toggle.changedNets()).contains(switchNet, ledNet);
        assertThat(engine.snapshot().netValue(ledNet)).isEqualTo(Value.ZERO);
    }

    @Test
    void aClockAlwaysHasANextEvent() {
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        engine.update(new FlatCircuit(List.of(new FlatComponent("k", standard.require(StandardComponentTypes.CLOCK),
                Map.of("time-up", 3, "time-down", 2))), List.of(net("clock", pin("k", 0)))));

        assertThat(engine.advanceToNextEvent()).isEqualTo(new StepResult(0, 2, Set.of("clock"), OptionalLong.of(5)));
        assertThat(engine.advanceToNextEvent()).isEqualTo(new StepResult(2, 5, Set.of("clock"), OptionalLong.of(7)));
        assertThat(engine.advanceToNextEvent().nextEventTime()).hasValue(10);
    }

    @Test
    void aZeroDelayLoopExceedsTheBudgetAndTrackingStops() {
        registerRinging();
        engine.update(new FlatCircuit(List.of(component("r", RINGING, Map.of())),
                List.of(net("loop", pin("r", 0), pin("r", 1)))));

        assertThatThrownBy(engine::advanceToNextEvent).isInstanceOf(IllegalStateException.class)
                .hasMessage("too many events at t = 5");
        assertThat(engine.now()).isEqualTo(5);
        assertThat(engine.interactTracked("r").changedNets()).as("tracking restarted empty").isEmpty();
    }

    @Test
    void interactingWithANonInteractiveOrUnknownComponentChangesNothing() {
        engine.update(inverterChain());
        engine.advance(5);

        assertThat(engine.interactTracked("n1")).isEqualTo(new StepResult(5, 5, Set.of(), OptionalLong.empty()));
        assertThat(engine.interactTracked("missing").changedNets()).isEmpty();
    }

    @Test
    void pendingComponentsAreTheOwnersOfTheNextTime() {
        engine.update(inverterChain());
        engine.advance(5);
        assertThat(engine.pendingComponents()).isEmpty();

        engine.interactTracked("s");
        assertThat(engine.pendingComponents()).containsExactly("n1");
        engine.advanceToNextEvent();
        assertThat(engine.pendingComponents()).containsExactly("n2");
        engine.advanceToNextEvent();
        assertThat(engine.pendingComponents()).isEmpty();
    }

    @Test
    void aNestedGateIsReportedByItsFlatId() {
        engine.update(new FlatCircuit(List.of(component("s", TestComponentTypes.SOURCE, Map.of("value", "0")),
                component("c4/n1", TestComponentTypes.INVERTER, Map.of("delay", 3))),
                List.of(net("a", pin("s", 0), pin("c4/n1", 0)), net("c4/b", pin("c4/n1", 1)))));
        engine.advance(5);

        engine.interactTracked("s");

        assertThat(engine.nextEventTime()).hasValue(8);
        assertThat(engine.pendingComponents()).containsExactly("c4/n1");
    }

    @Test
    void aRemovedComponentIsNeverPending() {
        engine.update(inverterChain());
        engine.advance(5);
        engine.interactTracked("s");

        engine.update(new FlatCircuit(List.of(component("s", TestComponentTypes.SOURCE, Map.of("value", "0"))),
                List.of(net("a", pin("s", 0)))));

        assertThat(engine.nextEventTime()).as("the removed inverter's event is still queued").hasValue(6);
        assertThat(engine.pendingComponents()).isEmpty();
    }
}
