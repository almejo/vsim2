package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.tunnel.TunnelCircuit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.core.tunnel.TunnelCircuit.parameters;
import static org.assertj.core.api.Assertions.assertThat;

class TunnelNetlistTest {

    private final TunnelCircuit circuit = new TunnelCircuit();

    private Component switchAt(int x, int y) {
        return circuit.place(StandardComponentTypes.SWITCH, x, y, Map.of());
    }

    private Component ledAt(int x, int y) {
        return circuit.place(StandardComponentTypes.LED, x, y, Map.of());
    }

    private Component byteConstantAt(int x, int y) {
        return circuit.place(StandardComponentTypes.CONSTANT, x, y,
                parameters(StandardComponentTypes.WIDTH, 8, "value", "5"));
    }

    @Test
    void tunnelsWithTheSameNameJoinTheirNets() {
        Component source = switchAt(0, 0);
        Component entrance = circuit.entrance("CLK", circuit.pin(source, 0));
        Component led = ledAt(40, 20);
        Component exit = circuit.exit("CLK", circuit.pin(led, 0));

        assertThat(circuit.netOf(source, 0)).isEqualTo(circuit.netOf(led, 0));
        assertThat(circuit.netOf(source, 0).pins()).contains(new PinRef(entrance.id(), 0), new PinRef(exit.id(), 0));
    }

    @Test
    void tunnelsWithDifferentNamesStayApart() {
        Component source = switchAt(0, 0);
        circuit.entrance("CLK", circuit.pin(source, 0));
        Component led = ledAt(40, 20);
        circuit.exit("RESET", circuit.pin(led, 0));

        assertThat(circuit.netOf(source, 0)).isNotEqualTo(circuit.netOf(led, 0));
    }

    @Test
    void namesAreCaseSensitive() {
        Component source = switchAt(0, 0);
        circuit.entrance("clk", circuit.pin(source, 0));
        Component led = ledAt(40, 20);
        circuit.exit("CLK", circuit.pin(led, 0));

        assertThat(circuit.netOf(source, 0)).isNotEqualTo(circuit.netOf(led, 0));
    }

    @Test
    void namesAreTrimmedBeforeJoining() {
        Component source = switchAt(0, 0);
        Component entrance = circuit.entrance("  CLK ", circuit.pin(source, 0));
        Component led = ledAt(40, 20);
        circuit.exit("CLK", circuit.pin(led, 0));

        assertThat(entrance.parameters().get("name")).isEqualTo("CLK");
        assertThat(circuit.netOf(source, 0)).isEqualTo(circuit.netOf(led, 0));
    }

    @Test
    void aTunnelOnAOneBitWireIsOneBitWide() {
        Component source = switchAt(0, 0);
        circuit.entrance("A", circuit.pin(source, 0));

        Net net = circuit.netOf(source, 0);
        assertThat(net.width()).isEqualTo(1);
        assertThat(net.widthMismatch()).isFalse();
    }

    @Test
    void aTunnelOnABusTakesTheWidthOfTheBus() {
        Component constant = byteConstantAt(0, 0);
        circuit.entrance("DATA", circuit.pin(constant, 0));

        Net net = circuit.netOf(constant, 0);
        assertThat(net.width()).isEqualTo(8);
        assertThat(net.widthMismatch()).isFalse();
    }

    @Test
    void aTunnelWhoseOnlyOtherSideIsABusFarAwayIsAsWideAsTheBus() {
        Component constant = byteConstantAt(0, 0);
        circuit.entrance("DATA", circuit.pin(constant, 0));
        Component exit = circuit.exit("DATA", new GridPoint(100, 100));
        circuit.wire(circuit.pin(exit, 0), new GridPoint(100, 90));

        Net net = circuit.netOf(exit, 0);
        assertThat(net).isEqualTo(circuit.netOf(constant, 0));
        assertThat(net.width()).isEqualTo(8);
        assertThat(net.widthMismatch()).isFalse();
    }

    @Test
    void aTunnelConnectedToNothingIsAOneBitNetWithoutMismatch() {
        Component lonely = circuit.exit("NOWHERE", new GridPoint(10, 10));

        Net net = circuit.netOf(lonely, 0);
        assertThat(net.width()).isEqualTo(1);
        assertThat(net.widthMismatch()).isFalse();
    }

    @Test
    void aBusMeetingAOneBitInputThroughATunnelIsAWidthMismatch() {
        Component constant = byteConstantAt(0, 0);
        circuit.entrance("DATA", circuit.pin(constant, 0));
        Component led = ledAt(40, 20);
        circuit.exit("DATA", circuit.pin(led, 0));

        Net net = circuit.netOf(led, 0);
        assertThat(net.width()).isEqualTo(8);
        assertThat(net.widthMismatch()).isTrue();
    }

    @Test
    void theDrawnNetlistDoesNotJoinTunnels() {
        Component source = switchAt(0, 0);
        circuit.entrance("CLK", circuit.pin(source, 0));
        Component led = ledAt(40, 20);
        circuit.exit("CLK", circuit.pin(led, 0));

        Netlist drawn = new NetlistCompiler(circuit.registry()).compileAsDrawn(circuit.document());

        assertThat(drawn.netForPin(new PinRef(source.id(), 0)))
                .isNotEqualTo(drawn.netForPin(new PinRef(led.id(), 0)));
    }
}
