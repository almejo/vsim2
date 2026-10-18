package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 7-segment displays' bus input (M7 follow-up): appended after the one-bit pins, on the bottom edge. A bus with any
 * non-Z bit is what the display shows; a fully Z bus leaves the one-bit pins in charge. Wiring both is a soft warning.
 */
class SevenSegmentsBusInputTest {

    private static final String DISPLAY = "display";
    private static final String BUS_NET = "bus";

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final ComponentType source = TestComponentTypes.registry().require(TestComponentTypes.SOURCE);
    private final SimulationEngine engine = new SimulationEngine();

    private ComponentType display(int width) {
        return registry.require(width == 4 ? StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY
                : StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE);
    }

    /**
     * A display of {@code width} bits. {@code bits[k]} is the value of a source on bit pin k ({@code null}: nothing
     * connected); {@code busBits} likewise feeds a joiner whose bus goes to the display's bus pin ({@code null}: no bus).
     */
    private SimulationSnapshot simulate(int width, String[] bits, String[] busBits) {
        List<FlatComponent> components = new ArrayList<>();
        List<FlatNet> nets = new ArrayList<>();
        components.add(new FlatComponent(DISPLAY, display(width), Map.of()));
        for (int bit = 0; bit < width; bit++) {
            if (bits != null && bits[bit] != null) {
                String sourceId = "bit" + bit;
                components.add(new FlatComponent(sourceId, source, Map.of("value", bits[bit])));
                nets.add(new FlatNet("n" + bit, List.of(new FlatPinRef(DISPLAY, bit), new FlatPinRef(sourceId, 0)),
                        1, false));
            }
        }
        if (busBits != null) {
            Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH, width);
            components.add(new FlatComponent("joiner",
                    registry.require(StandardComponentTypes.BUS_JOINER).specializedFor(parameters), parameters));
            nets.add(new FlatNet(BUS_NET, List.of(new FlatPinRef(DISPLAY, width), new FlatPinRef("joiner", 0)),
                    width, false));
            for (int bit = 0; bit < width; bit++) {
                if (busBits[bit] != null) {
                    String sourceId = "busBit" + bit;
                    components.add(new FlatComponent(sourceId, source, Map.of("value", busBits[bit])));
                    nets.add(new FlatNet("b" + bit, List.of(new FlatPinRef("joiner", bit + 1),
                            new FlatPinRef(sourceId, 0)), 1, false));
                }
            }
        }
        engine.update(new FlatCircuit(1, components, nets));
        engine.advance(3);
        return engine.snapshot();
    }

    private static Value shown(SimulationSnapshot snapshot) {
        return ((VisibleState.SevenSegmentsState) snapshot.componentState(DISPLAY)).value();
    }

    /** Bit k of {@code number} as a source value, for {@code width} bits. */
    private static String[] binary(int number, int width) {
        String[] values = new String[width];
        for (int bit = 0; bit < width; bit++) {
            values[bit] = (number >> bit & 1) == 1 ? "1" : "0";
        }
        return values;
    }

    @Test
    void theBusPinIsAppendedOnTheBottomEdgeCentredAndTheBitPinsStayWhereTheyWere() {
        ComponentType single = display(4);
        assertThat(single.pinCount()).isEqualTo(5);
        for (int bit = 0; bit < 4; bit++) {
            assertThat(single.pin(bit)).isEqualTo(PinSpec.input("in" + bit, 4 - bit, 0));
        }
        assertThat(single.pin(4)).isEqualTo(new PinSpec("bus", new GridPoint(2, 6), PinDirection.INPUT, 4));

        ComponentType twoDigits = display(8);
        assertThat(twoDigits.pinCount()).isEqualTo(9);
        for (int bit = 0; bit < 8; bit++) {
            assertThat(twoDigits.pin(bit)).isEqualTo(PinSpec.input("in" + bit, 8 - bit, 0));
        }
        assertThat(twoDigits.pin(8)).isEqualTo(new PinSpec("bus", new GridPoint(4, 6), PinDirection.INPUT, 8));
        assertThat(single.size().height()).isEqualTo(6);
        assertThat(twoDigits.size().height()).isEqualTo(6);
    }

    @Test
    void withOnlyTheBitPinsTheDisplayCombinesThemAsBefore() {
        assertThat(shown(simulate(4, binary(0x9, 4), null))).isEqualTo(Value.of(4, 0x9));
        assertThat(shown(simulate(8, binary(0xA5, 8), null))).isEqualTo(Value.of(8, 0xA5));
    }

    @Test
    void withOnlyTheBusTheDisplayShowsTheBus() {
        assertThat(shown(simulate(4, null, binary(0x6, 4)))).isEqualTo(Value.of(4, 0x6));
        SimulationSnapshot snapshot = simulate(8, null, binary(0x5A, 8));
        assertThat(shown(snapshot)).isEqualTo(Value.of(8, 0x5A));
        assertThat(snapshot.problems()).isEmpty();
    }

    @Test
    void withBothTheBusWinsAndASoftWarningNamesTheDisplay() {
        for (int width : new int[]{4, 8}) {
            SimulationSnapshot snapshot = simulate(width, binary(0x3, width), binary(0xC, width));
            assertThat(shown(snapshot)).as("width %d", width).isEqualTo(Value.of(width, 0xC));
            assertThat(snapshot.problems()).as("width %d", width)
                    .containsExactly(new DisplayBusOverride(BUS_NET, DISPLAY));
            assertThat(snapshot.shortCircuits()).isEmpty();
            assertThat(snapshot.widthMismatches()).isEmpty();
        }
    }

    @Test
    void aFullyZBusFallsBackToTheBitPins() {
        String[] undriven = new String[4];
        assertThat(shown(simulate(4, binary(0x5, 4), undriven))).isEqualTo(Value.of(4, 0x5));
        String[] allZ = {"Z", "Z", "Z", "Z"};
        assertThat(shown(simulate(4, binary(0xE, 4), allZ))).isEqualTo(Value.of(4, 0xE));
        assertThat(shown(simulate(4, null, allZ))).isEqualTo(Value.highImpedance(4));
    }

    @Test
    void aBusWithSomeZBitsIsShownWithThoseBitsZ() {
        String[] partial = {"1", "Z", "0", null};
        Value shown = shown(simulate(4, binary(0xF, 4), partial));
        assertThat(shown).isEqualTo(new Value(4, 0b0001, 0b1010, false));
    }

    @Test
    void theBitPinsAloneKeepShowingPartialZAsBefore() {
        String[] bits = {"1", null, "Z", "1"};
        assertThat(shown(simulate(4, bits, null))).isEqualTo(new Value(4, 0b1001, 0b0110, false));
    }

    @Test
    void theWarningIsAboutWiringAndGoesAwayWhenOneSideIsDisconnected() {
        // A bus whose drivers are all Z still counts: it is wired to a driver, so the bits are ignored once it drives.
        String[] allZ = {"Z", "Z", "Z", "Z"};
        assertThat(simulate(4, binary(0x1, 4), allZ).problems())
                .containsExactly(new DisplayBusOverride(BUS_NET, DISPLAY));
        assertThat(simulate(4, binary(0x1, 4), null).problems()).isEmpty();
        assertThat(simulate(4, null, binary(0x1, 4)).problems()).isEmpty();
        String[] oneBit = {null, null, "1", null};
        assertThat(simulate(4, oneBit, binary(0x1, 4)).problems())
                .containsExactly(new DisplayBusOverride(BUS_NET, DISPLAY));
    }

    @Test
    void wiringABusAndABitToADrawnDisplayRaisesTheWarningAndUnwiringTheBitClearsIt() {
        CircuitDocument document = new CircuitDocument();
        Component display = place(document, StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, new GridPoint(10, 10),
                Map.of());
        Component bitSwitch = place(document, StandardComponentTypes.SWITCH, new GridPoint(10, 2),
                Map.of("value", "1"));
        Map<String, Object> constantParameters = Map.of(StandardComponentTypes.WIDTH, 4, "value", "0xA",
                "format", "auto");
        Component constant = place(document, StandardComponentTypes.CONSTANT, new GridPoint(0, 19),
                constantParameters);
        GridPoint switchPin = registry.typeOf(bitSwitch).pinLocation(bitSwitch, 0);
        GridPoint bitPin = registry.typeOf(display).pinLocation(display, 0);
        GridPoint busPin = registry.typeOf(display).pinLocation(display, 4);
        GridPoint constantPin = registry.typeOf(constant).pinLocation(constant, 0);
        assertThat(busPin).isEqualTo(new GridPoint(12, 16));
        assertThat(switchPin.x()).as("the switch sits right above bit 0").isEqualTo(bitPin.x());
        Wire bitWire = new Wire(document.newWireId(), switchPin, bitPin);
        document.add(bitWire);
        GridPoint corner = new GridPoint(busPin.x(), constantPin.y());
        document.add(new Wire(document.newWireId(), constantPin, corner));
        document.add(new Wire(document.newWireId(), corner, busPin));

        SimulationSnapshot both = simulate(document);
        assertThat(shown(both, display)).isEqualTo(Value.of(4, 0xA));
        assertThat(both.problems()).singleElement().isInstanceOfSatisfying(DisplayBusOverride.class,
                warning -> assertThat(warning.componentId()).isEqualTo(display.id().value()));

        document.remove(bitWire.id());
        SimulationSnapshot busOnly = simulate(document);
        assertThat(shown(busOnly, display)).isEqualTo(Value.of(4, 0xA));
        assertThat(busOnly.problems()).isEmpty();
    }

    private Component place(CircuitDocument document, String typeId, GridPoint position,
                            Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), position, Rotation.R0,
                parameters, "");
        document.add(component);
        return component;
    }

    private SimulationSnapshot simulate(CircuitDocument document) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        engine.update(new CircuitFlattener(registry).flatten(document, netlist));
        engine.advance(3);
        return engine.snapshot();
    }

    private static Value shown(SimulationSnapshot snapshot, Component display) {
        return ((VisibleState.SevenSegmentsState) snapshot.componentState(display.id().value())).value();
    }
}
