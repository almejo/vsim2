package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.ComponentDrawing;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A bus output wired with a wire shorter than its value tag keeps the tag outside the piece's body. */
class CircuitRendererBusTagTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private CircuitDocument document;

    private Rectangle2D body;

    private BufferedImage render(ColorScheme scheme, boolean busValues) {
        document = new CircuitDocument();
        Component register = registry.require(StandardComponentTypes.REGISTER).newComponent(
                document.newComponentId(), new GridPoint(6, 6), Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 4), "");
        document.add(register);
        ComponentType type = registry.typeOf(register);
        body = ComponentDrawing.documentBounds(type, register);
        PinSpec output = type.pins().stream().filter(PinSpec::isDriver).findFirst().orElseThrow();
        GridPoint pin = new GridPoint(register.position().x() + output.position().x(),
                register.position().y() + output.position().y());
        document.add(new Wire(new ElementId("w1"), pin, new GridPoint(pin.x() + 2, pin.y())));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String bus = netlist.netForWire(new ElementId("w1")).orElseThrow().id().value();
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true,
                Map.of(bus, Value.of(4, 0xA)), Map.of(), List.of(), Optional.empty());
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, 400, 300);
        new CircuitRenderer(registry, PainterRegistry.standard(), scheme).paint(graphics, new RenderState(document,
                netlist, document.revision(), snapshot, Set.of(), 0, 0, Set.of(),
                PaintPolicy.CANVAS.withBusValues(busValues)));
        graphics.dispose();
        String path = System.getenv("VSIM_BUS_TAG_PNG");
        if (path != null && busValues) {
            try {
                javax.imageio.ImageIO.write(image, "png", new File(path));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
        return image;
    }

    @Test
    void theTagOfAShortBusWireDoesNotTouchTheBody() {
        for (ColorScheme scheme : List.of(ColorScheme.LIGHT, ColorSchemes.DARK)) {
            BufferedImage withTag = render(scheme, true);
            Rectangle2D shownBody = body;
            BufferedImage withoutTag = render(scheme, false);
            for (int pixelY = (int) shownBody.getMinY(); pixelY < shownBody.getMaxY(); pixelY++) {
                for (int pixelX = (int) shownBody.getMinX(); pixelX < shownBody.getMaxX(); pixelX++) {
                    assertThat(withTag.getRGB(pixelX, pixelY)).as("%d,%d", pixelX, pixelY)
                            .isEqualTo(withoutTag.getRGB(pixelX, pixelY));
                }
            }
        }
    }
}
