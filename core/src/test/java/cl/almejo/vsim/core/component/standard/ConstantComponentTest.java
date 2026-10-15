package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstantComponentTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentType constant = registry.require(StandardComponentTypes.CONSTANT);

    private static Map<String, Object> parameters(int width, String value, String format) {
        return Map.of("width", width, "value", value, "format", format);
    }

    @Test
    void theDefaultsAreEightBitsZeroInAutoFormat() {
        assertThat(constant.resolveParameters(Map.of()))
                .isEqualTo(Map.of("width", 8, "value", "0", "format", "auto"));
        assertThat(constant.category()).isEqualTo(ComponentCategory.INPUT_OUTPUT);
    }

    @Test
    void editingOnlyTheValueNeverChangesTheShapeOrThePin() {
        for (String format : new String[]{"auto", "binary", "hex", "decimal"}) {
            for (int width : new int[]{1, 4, 5, 8, 16, 32}) {
                ComponentType low = constant.specializedFor(parameters(width, "0", format));
                ComponentType high = constant.specializedFor(parameters(width, "0b1", format));
                assertThat(high.size()).as("%s %d", format, width).isEqualTo(low.size());
                assertThat(high.pins()).as("%s %d", format, width).isEqualTo(low.pins());
            }
        }
        ComponentType first = constant.specializedFor(parameters(8, "0x5", "hex"));
        ComponentType second = constant.specializedFor(parameters(8, "0xFF", "hex"));
        assertThat(first.size()).isEqualTo(second.size());
        assertThat(first.pins()).isEqualTo(second.pins());
    }

    @Test
    void theShapeGrowsWithTheWidthInBinaryAndTheHeightIsTwo() {
        ComponentType narrow = constant.specializedFor(parameters(4, "0", "binary"));
        ComponentType wide = constant.specializedFor(parameters(32, "0", "binary"));
        assertThat(wide.size().width()).isGreaterThan(narrow.size().width());
        assertThat(narrow.size().height()).isEqualTo(2);
        assertThat(wide.size().height()).isEqualTo(2);
        assertThat(wide.pin(0).position()).isEqualTo(new GridPoint(wide.size().width(), 1));
    }

    @Test
    void theOutputPinIsAsWideAsTheConstant() {
        for (int width : new int[]{1, 2, 8, 32}) {
            ComponentType specialized = constant.specializedFor(parameters(width, "0", "auto"));
            assertThat(specialized.pinCount()).isEqualTo(1);
            assertThat(specialized.pin(0).width()).isEqualTo(width);
            assertThat(specialized.pin(0).direction()).isEqualTo(PinDirection.OUTPUT);
        }
    }

    @Test
    void theValueIsStoredNormalized() {
        Component component = constant.newComponent(new ElementId("c1"), GridPoint.ORIGIN, Rotation.R0,
                parameters(8, " 0x00ff ", "auto"), "");
        assertThat(component.parameters().get("value")).isEqualTo("0xFF");
    }

    @Test
    void theCheckRefusesWhatDoesNotFitOrIsNotANumber() {
        assertThat(constant.check(parameters(4, "0x1F", "auto"))).contains(
                new ParameterProblem("value", "constant.doesNotFit", List.of(4)));
        assertThat(constant.check(parameters(2, "0b111", "auto"))).contains(
                new ParameterProblem("value", "constant.doesNotFit", List.of(2)));
        assertThat(constant.check(parameters(4, "12a", "auto"))).contains(
                new ParameterProblem("value", "constant.invalidValue", List.of()));
        assertThat(constant.check(parameters(4, "", "auto"))).contains(
                new ParameterProblem("value", "constant.invalidValue", List.of()));
        assertThat(constant.check(parameters(4, "-1", "auto"))).contains(
                new ParameterProblem("value", "constant.invalidValue", List.of()));
        assertThat(constant.check(parameters(4, "0xF", "auto"))).isEmpty();
        assertThat(constant.check(parameters(32, "0xFFFFFFFF", "auto"))).isEmpty();
        assertThat(constant.check(parameters(31, "0xFFFFFFFF", "auto"))).isPresent();
        assertThat(registry.require(StandardComponentTypes.SWITCH).check(Map.of())).isEqualTo(Optional.empty());
    }

    @Test
    void resolvingAndSpecializingRefuseWhatTheCheckRefuses() {
        assertThatThrownBy(() -> constant.resolveParameters(parameters(4, "0x1F", "auto")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> constant.specializedFor(parameters(2, "0b111", "auto")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> constant.newComponent(new ElementId("c1"), GridPoint.ORIGIN, Rotation.R0,
                parameters(4, "nope", "auto"), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> constant.createBehavior(parameters(4, "0x1F", "auto")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFileWithAConstantThatDoesNotFitFailsToLoad() {
        CircuitFormat format = new CircuitFormat(registry);
        assertThatThrownBy(() -> format.read("""
                {"formatVersion":2,"kind":"circuit","components":[{"id":"c1","type":"constant","x":1,"y":1,
                "parameters":{"width":4,"value":"0x1F"}}]}
                """)).isInstanceOf(CircuitFormatException.class);
    }

    @Test
    void aFileWithAFittingConstantLoadsNormalized() throws CircuitFormatException {
        CircuitDocument loaded = new CircuitFormat(registry).read("""
                {"formatVersion":2,"kind":"circuit","components":[{"id":"c1","type":"constant","x":1,"y":1,
                "parameters":{"width":8,"value":"0x00ff"}}]}
                """);
        assertThat(loaded.components().getFirst().parameters())
                .isEqualTo(Map.of("width", 8, "value", "0xFF", "format", "auto"));
    }

    private Component place(CircuitDocument document, String id, ComponentType type, int x, int y,
                            Map<String, Object> rawParameters) {
        Component component = type.newComponent(new ElementId(id), new GridPoint(x, y), Rotation.R0, rawParameters, "");
        document.add(component);
        return component;
    }

    private SimulationEngine simulate(CircuitDocument document, Netlist netlist) {
        SimulationEngine engine = new SimulationEngine();
        engine.update(new CircuitFlattener(registry).flatten(document, netlist));
        return engine;
    }

    @Test
    void aConstantDrivesItsValueThroughASplitterAndNeverSchedulesAgain() {
        CircuitDocument document = new CircuitDocument();
        Component source = place(document, "c1", constant, 0, 0, parameters(4, "0x5", "auto"));
        ComponentType splitter = registry.require(StandardComponentTypes.BUS_SPLITTER);
        GridPoint sourcePin = registry.typeOf(source).pinLocation(source, 0);
        GridPoint splitterOrigin = new GridPoint(sourcePin.x() + 2, 1);
        Component split = place(document, "c2", splitter, splitterOrigin.x(), splitterOrigin.y(),
                Map.of(StandardComponentTypes.WIDTH, 4));
        document.add(new Wire(document.newWireId(), sourcePin, splitterOrigin));
        for (int bit = 0; bit < 4; bit++) {
            GridPoint pin = registry.typeOf(split).pinLocation(split, bit + 1);
            document.add(new Wire(document.newWireId(), pin, new GridPoint(pin.x() + 1, pin.y())));
        }
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationEngine engine = simulate(document, netlist);

        engine.runUntil(10);

        SimulationSnapshot snapshot = engine.snapshot();
        String busNet = netlist.netForPin(new PinRef(source.id(), 0)).orElseThrow().id().value();
        assertThat(snapshot.netValue(busNet)).isEqualTo(Value.of(4, 0b0101));
        int[] expected = {1, 0, 1, 0};
        for (int bit = 0; bit < 4; bit++) {
            String bitNet = netlist.netForPin(new PinRef(split.id(), bit + 1)).orElseThrow().id().value();
            assertThat(snapshot.netValue(bitNet)).as("bit %d", bit).isEqualTo(Value.of(expected[bit] == 1));
        }
        assertThat(engine.nextEventTime()).isEmpty();
        assertThat(snapshot.componentState("c1")).isInstanceOf(VisibleState.ConstantState.class);
    }

    @Test
    void aConstantAtTheTopOfThirtyTwoBitsDrivesAllItsBits() {
        CircuitDocument document = new CircuitDocument();
        Component source = place(document, "c1", constant, 0, 0, parameters(32, "4294967295", "decimal"));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationEngine engine = simulate(document, netlist);
        engine.runUntil(5);
        String net = netlist.netForPin(new PinRef(source.id(), 0)).orElseThrow().id().value();
        assertThat(engine.snapshot().netValue(net)).isEqualTo(Value.of(32, 0xFFFFFFFFL));
    }

    @Test
    void aConstantIsNotInteractive() {
        assertThat(constant.createBehavior(Map.of()).isInteractive()).isFalse();
    }

    @Test
    void aConstantInsideAUserComponentDrivesTheSameValue() {
        Map<String, Object> eightBits = Map.of(InterfacePins.WIDTH, 8);
        CircuitDocument inside = new CircuitDocument();
        Component inner = place(inside, "c1", constant, 0, 0, parameters(8, "0xA5", "hex"));
        Component output = place(inside, "c2", registry.require(InterfacePins.OUTPUT_PIN),
                registry.typeOf(inner).size().width(), 0, eightBits);
        ComponentDefinition definition = new ComponentDefinition("holder", "Holder", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.output("y", 4, 2, 8), output.id())),
                new CircuitFragment(List.of(inner, output), List.of(), List.of()));

        CircuitDocument outer = new CircuitDocument();
        outer.putDefinition(definition);
        registry.addResolver(SubcircuitTypes.resolverFor(outer));
        Component instance = new Component(new ElementId("i1"), SubcircuitTypes.typeIdOf("holder"), GridPoint.ORIGIN,
                Rotation.R0, Map.of(), "");
        outer.add(instance);
        Netlist netlist = new NetlistCompiler(registry).compile(outer);
        SimulationEngine engine = simulate(outer, netlist);

        engine.runUntil(10);

        String net = netlist.netForPin(new PinRef(instance.id(), 0)).orElseThrow().id().value();
        assertThat(engine.snapshot().netValue(net)).isEqualTo(Value.of(8, 0xA5));
    }
}
