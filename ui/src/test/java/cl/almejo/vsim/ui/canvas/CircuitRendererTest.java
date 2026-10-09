package cl.almejo.vsim.ui.canvas;

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
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitRendererTest {

    private static final ColorScheme SCHEME = ColorScheme.LIGHT;
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(), SCHEME);
    private final CircuitDocument document = new CircuitDocument();
    private final Wire wire = new Wire(document.newWireId(), new GridPoint(1, 1), new GridPoint(5, 1));

    private BufferedImage render(SimulationSnapshot snapshot, Netlist netlist, long token) {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 120);
        renderer.paint(graphics, new RenderState(document, netlist, token, snapshot, Set.of()));
        graphics.dispose();
        return image;
    }

    private static SimulationSnapshot snapshot(long token, Map<String, Value> values, List<ShortCircuit> shorts) {
        return new SimulationSnapshot(1, token, 0, true, values, Map.of(), List.copyOf(shorts), Optional.empty());
    }

    @Test
    void wiresTakeTheColorOfTheirNetWhenTheSnapshotMatches() {
        document.add(wire);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        BufferedImage image = render(snapshot(document.revision(), Map.of(netId, Value.ONE), List.of()), netlist, document.revision());
        assertThat(new Color(image.getRGB(24, 8))).isEqualTo(SCHEME.wireOne());
    }

    @Test
    void staleSnapshotPaintsWiresInTheUnknownColor() {
        document.add(wire);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        BufferedImage image = render(snapshot(document.revision() - 1, Map.of(netId, Value.ONE), List.of()), netlist,
                document.revision());
        assertThat(new Color(image.getRGB(24, 8))).isEqualTo(SCHEME.wireUnknown());
    }

    @Test
    void shortCircuitThickensTheNetAndMarksItsDrivers() {
        Component high = registry.require(StandardComponentTypes.SWITCH).newComponent(document.newComponentId(),
                new GridPoint(-3, 0), Rotation.R0, Map.of("value", "1"), "");
        document.add(high);
        document.add(wire);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForWire(wire.id()).orElseThrow().id().value();
        ShortCircuit shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE)), 0);
        BufferedImage image = render(snapshot(document.revision(), Map.of(netId, Value.ONE.withConflict()),
                List.of(shortCircuit)), netlist, document.revision());
        assertThat(new Color(image.getRGB(24, 9))).as("thick conflict wire").isEqualTo(SCHEME.wireConflict());
        Color halo = new Color(image.getRGB(8, 3));
        assertThat(halo.getRed()).as("halo above the driver pin").isGreaterThan(halo.getGreen() + 40);
    }
}
