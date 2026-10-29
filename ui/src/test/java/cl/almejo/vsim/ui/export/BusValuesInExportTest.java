package cl.almejo.vsim.ui.export;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The PNG/SVG export follows the canvas: with «Valores de los buses» off, no bus label is painted. */
class BusValuesInExportTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final Wire wire = new Wire(document.newWireId(), new GridPoint(1, 1), new GridPoint(5, 1));

    /** The image of a 4-bit bus holding 5. */
    private CircuitImage busImage(boolean current) {
        document.add(wire);
        document.add(registry.require(StandardComponentTypes.BUS_SPLITTER).newComponent(document.newComponentId(),
                new GridPoint(5, 1), Rotation.R0, Map.of(StandardComponentTypes.WIDTH, 4), ""));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String busNet = netlist.netForWire(wire.id()).orElseThrow().id().value();
        long token = current ? document.revision() : document.revision() - 1;
        SimulationSnapshot snapshot = new SimulationSnapshot(1, token, 0, true,
                Map.of(busNet, Value.of(4, 0x5)), Map.of(), List.of(), Optional.empty());
        return CircuitImage.of(document, netlist, document.revision(), snapshot, registry,
                PainterRegistry.standard(), ColorScheme.LIGHT, Optional.empty()).orElseThrow();
    }

    private static BufferedImage render(CircuitImage image) {
        BufferedImage result = new BufferedImage(image.width(1), image.height(1), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        image.paint(graphics, 1);
        graphics.dispose();
        return result;
    }

    private static boolean sameImage(BufferedImage first, BufferedImage second) {
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void theImageWritesTheBusValueByDefaultAndLeavesItOutWhenToldTo() {
        CircuitImage shown = busImage(true);
        BufferedImage withLabel = render(shown);
        BufferedImage withoutLabel = render(shown.withBusValues(false));

        assertThat(sameImage(withLabel, withoutLabel)).isFalse();
        assertThat(sameImage(withLabel, render(shown.withBusValues(true)))).isTrue();
    }

    @Test
    void withoutTheLabelOnlyTheLabelIsGone() {
        CircuitImage shown = busImage(true);
        BufferedImage withLabel = render(shown);
        BufferedImage withoutLabel = render(shown.withBusValues(false));
        // the wire's row in the image: (1 - top of the bounds) grid units of 8 px
        int wireRow = (1 - shown.gridBounds().y()) * 8;

        int differing = 0;
        for (int y = 0; y < withLabel.getHeight(); y++) {
            for (int x = 0; x < withLabel.getWidth(); x++) {
                if (withLabel.getRGB(x, y) != withoutLabel.getRGB(x, y)) {
                    differing++;
                    assertThat(y).as("row of a changed pixel").isLessThan(wireRow);
                }
            }
        }
        assertThat(differing).isGreaterThan(5);
    }

    @Test
    void anExplicitPolicyIsTakenAsItIs() {
        CircuitImage image = busImage(true);
        BufferedImage result = new BufferedImage(image.width(1), image.height(1), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        image.paint(graphics, 1, PaintPolicy.IMAGE.withBusValues(false));
        graphics.dispose();

        assertThat(sameImage(result, render(image.withBusValues(false)))).isTrue();
    }
}
