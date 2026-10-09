package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.RenderState;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCircuitPulseTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final Component high = place(0, 0, "1");
    private final Component low = place(0, 6, "0");
    private final Wire joint = wire();
    private final Netlist netlist = new NetlistCompiler(registry).compile(document);
    private final AtomicLong nanos = new AtomicLong();

    private Component place(int x, int y, String value) {
        Component component = registry.require(StandardComponentTypes.SWITCH)
                .newComponent(document.newComponentId(), new GridPoint(x, y), Rotation.R0, Map.of("value", value), "");
        document.add(component);
        return component;
    }

    private Wire wire() {
        Wire wire = new Wire(document.newWireId(), new GridPoint(4, 1), new GridPoint(4, 7));
        document.add(wire);
        return wire;
    }

    private SimulationSnapshot withShort() {
        String netId = netlist.netForWire(joint.id()).orElseThrow().id().value();
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE),
                new DriverRef(low.id().value(), 0, Value.ZERO)), 5);
        return new SimulationSnapshot(1, document.revision(), 10, true, Map.of(), Map.of(), List.of(shortCircuit),
                Optional.empty());
    }

    private int pixelOnTheWire(ShortCircuitPulse pulse, SimulationSnapshot snapshot) {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        pulse.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, Set.of()));
        graphics.dispose();
        return image.getRGB(32, 32);
    }

    @Test
    void alphaRisesAndFallsOverOnePeriod() {
        assertThat(ShortCircuitPulse.alphaAt(0)).isZero();
        assertThat(ShortCircuitPulse.alphaAt(ShortCircuitPulse.PERIOD_NANOS / 2))
                .isEqualTo(ShortCircuitPulse.MAXIMUM_ALPHA);
        assertThat(ShortCircuitPulse.alphaAt(ShortCircuitPulse.PERIOD_NANOS)).isZero();
        assertThat(ShortCircuitPulse.alphaAt(-ShortCircuitPulse.PERIOD_NANOS / 2))
                .isEqualTo(ShortCircuitPulse.MAXIMUM_ALPHA);
    }

    @Test
    void paintsAVeilOverShortedWiresThatFollowsTheClock() {
        ShortCircuitPulse pulse = new ShortCircuitPulse(registry, () -> { }, nanos::get);
        assertThat(pixelOnTheWire(pulse, withShort())).isZero();
        nanos.set(ShortCircuitPulse.PERIOD_NANOS / 2);
        assertThat(pixelOnTheWire(pulse, withShort())).isNotZero();
        assertThat(pixelOnTheWire(pulse, SimulationSnapshot.EMPTY)).isZero();
    }

    @Test
    void theRepaintTimerRunsOnlyWhileShortsExist() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ShortCircuitPulse pulse = new ShortCircuitPulse(registry, () -> { }, nanos::get);
            assertThat(pulse.isPulsing()).isFalse();
            pulse.showSnapshot(withShort());
            assertThat(pulse.isPulsing()).isTrue();
            pulse.showSnapshot(withShort());
            assertThat(pulse.isPulsing()).isTrue();
            pulse.showSnapshot(SimulationSnapshot.EMPTY);
            assertThat(pulse.isPulsing()).isFalse();
            pulse.showSnapshot(withShort());
            pulse.dispose();
            assertThat(pulse.isPulsing()).isFalse();
        });
    }
}
