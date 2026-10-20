package cl.almejo.vsim.core.tunnel;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TunnelSimulationTest {

    static final String TUNNEL_BUFFER = "tunnel-buffer";

    private final TunnelCircuit circuit = new TunnelCircuit();

    /**
     * A 4 x 4 pass-through whose body carries the signal through tunnel «A»: input-pin c1 (pin at 2,2) → entrada c2,
     * salida c3 → output-pin c4 (pin at 12,2). Pins a at (0,2) and y at (4,2).
     */
    static ComponentDefinition tunnelBuffer(String exitName) {
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        Component input = standard.require(InterfacePins.INPUT_PIN).newComponent(new ElementId("c1"),
                new GridPoint(0, 1), Rotation.R0, Map.of(), "a");
        Component entrance = standard.require(StandardComponentTypes.TUNNEL_ENTRANCE).newComponent(
                new ElementId("c2"), new GridPoint(2, 1), Rotation.R0, Map.of(Tunnels.NAME, "A"), "");
        // A salida's pin is at the right end of its tag, so it sits left of the output pin
        int exitX = 12 - (1 + Tunnels.lengthInGridUnits(exitName));
        Component exit = standard.require(StandardComponentTypes.TUNNEL_EXIT).newComponent(new ElementId("c3"),
                new GridPoint(exitX, 1), Rotation.R0, Map.of(Tunnels.NAME, exitName), "");
        Component output = standard.require(InterfacePins.OUTPUT_PIN).newComponent(new ElementId("c4"),
                new GridPoint(12, 1), Rotation.R0, Map.of(), "y");
        return new ComponentDefinition(TUNNEL_BUFFER, "Búfer por túnel", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id()),
                        new DefinitionPin(PinSpec.output("y", 4, 2), output.id())),
                new CircuitFragment(List.of(input, entrance, exit, output), List.of(), List.of()));
    }

    private Component switchAt(int x, int y, String value) {
        return circuit.place(StandardComponentTypes.SWITCH, x, y, Map.of("value", value));
    }

    /** Switch (out at x+4, y+1) → instance of the tunnel buffer (a at x+4, y+1; y at x+8, y+1) → LED (in at x+8, y+1). */
    private Component switchThroughBufferToLed(int x, int y, String value) {
        switchAt(x, y, value);
        circuit.place(SubcircuitTypes.typeIdOf(TUNNEL_BUFFER), x + 4, y - 1, Map.of());
        return circuit.place(StandardComponentTypes.LED, x + 7, y - 2, Map.of());
    }

    @Test
    void aSignalThroughTunnelsSimulatesLikeADrawnWire() {
        for (String value : List.of("0", "1")) {
            TunnelCircuit tunneled = new TunnelCircuit();
            Component source = tunneled.place(StandardComponentTypes.SWITCH, 0, 0, Map.of("value", value));
            tunneled.entrance("SIGNAL", tunneled.pin(source, 0));
            Component gate = tunneled.place(StandardComponentTypes.NOT, 30, 9, Map.of());
            tunneled.exit("SIGNAL", tunneled.pin(gate, 0));

            TunnelCircuit drawn = new TunnelCircuit();
            Component drawnSource = drawn.place(StandardComponentTypes.SWITCH, 0, 0, Map.of("value", value));
            Component drawnGate = drawn.place(StandardComponentTypes.NOT, 30, 9, Map.of());
            GridPoint corner = new GridPoint(drawn.pin(drawnGate, 0).x(), drawn.pin(drawnSource, 0).y());
            drawn.wire(drawn.pin(drawnSource, 0), corner);
            drawn.wire(corner, drawn.pin(drawnGate, 0));

            assertThat(tunneled.valueAt(tunneled.simulate(), gate, 1))
                    .isEqualTo(drawn.valueAt(drawn.simulate(), drawnGate, 1))
                    .isEqualTo("1".equals(value) ? Value.ZERO : Value.ONE);
        }
    }

    @Test
    void aBusThroughATunnelToAOneBitInputIsReportedAsAWidthMismatch() {
        Component constant = circuit.place(StandardComponentTypes.CONSTANT, 0, 0,
                TunnelCircuit.parameters(StandardComponentTypes.WIDTH, 8, "value", "5"));
        circuit.entrance("DATA", circuit.pin(constant, 0));
        Component led = circuit.place(StandardComponentTypes.LED, 40, 20, Map.of());
        circuit.exit("DATA", circuit.pin(led, 0));

        SimulationSnapshot snapshot = circuit.simulate();

        assertThat(snapshot.widthMismatches()).singleElement()
                .satisfies(mismatch -> assertThat(mismatch.pins()).extracting(pin -> pin.componentId())
                        .containsExactlyInAnyOrder(constant.id().value(), led.id().value()));
    }

    @Test
    void aBusThroughTunnelsReachesTheOtherSide() {
        Component constant = circuit.place(StandardComponentTypes.CONSTANT, 0, 0,
                TunnelCircuit.parameters(StandardComponentTypes.WIDTH, 8, "value", "5"));
        circuit.entrance("DATA", circuit.pin(constant, 0));
        Component exit = circuit.exit("DATA", new GridPoint(60, 60));
        circuit.wire(circuit.pin(exit, 0), new GridPoint(60, 50));

        SimulationSnapshot snapshot = circuit.simulate();

        assertThat(circuit.valueAt(snapshot, exit, 0)).isEqualTo(Value.of(8, 5));
    }

    @Test
    void tunnelsAreNotSimulatedTheyOnlyJoinNets() {
        Component source = switchAt(0, 0, "1");
        circuit.entrance("SIGNAL", circuit.pin(source, 0));

        assertThat(circuit.flatten().components()).extracting(FlatComponent::id)
                .containsExactly(source.id().value());
    }

    @Test
    void twoInstancesOfOneComponentEachKeepTheirOwnTunnels() {
        circuit.document().putDefinition(tunnelBuffer("A"));
        Component lit = switchThroughBufferToLed(0, 0, "1");
        Component dark = switchThroughBufferToLed(0, 20, "0");

        SimulationSnapshot snapshot = circuit.simulate();

        assertThat(circuit.valueAt(snapshot, lit, 0)).isEqualTo(Value.ONE);
        assertThat(circuit.valueAt(snapshot, dark, 0)).isEqualTo(Value.ZERO);
        assertThat(snapshot.shortCircuits()).isEmpty();
    }

    @Test
    void theSameNameInsideAComponentAndInTheTopCircuitStaysSeparate() {
        circuit.document().putDefinition(tunnelBuffer("A"));
        Component insideLed = switchThroughBufferToLed(0, 0, "0");
        Component topSource = switchAt(0, 40, "1");
        circuit.entrance("A", circuit.pin(topSource, 0));
        Component topLed = circuit.place(StandardComponentTypes.LED, 40, 60, Map.of());
        circuit.exit("A", circuit.pin(topLed, 0));

        SimulationSnapshot snapshot = circuit.simulate();

        assertThat(circuit.valueAt(snapshot, insideLed, 0)).isEqualTo(Value.ZERO);
        assertThat(circuit.valueAt(snapshot, topLed, 0)).isEqualTo(Value.ONE);
        assertThat(snapshot.shortCircuits()).isEmpty();
    }
}
