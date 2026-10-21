package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** How the canvas draws tunnels: the salida's colour comes from its entrada, and the stub is a wire. */
class CircuitRendererTunnelsTest {

    private static final int UNIT = GridPoint.PIXELS_PER_UNIT;
    private static final int IMAGE_SIZE = 200;
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();

    private Component add(TunnelKind kind, GridPoint position, Map<String, Object> parameters) {
        Component tunnel = registry.require(kind.typeId()).newComponent(document.newComponentId(), position,
                Rotation.R0, parameters, "");
        document.add(tunnel);
        return tunnel;
    }

    private BufferedImage render(ColorScheme scheme, Map<String, Value> values) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, values, Map.of(),
                List.of(), Optional.empty());
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        new CircuitRenderer(registry, PainterRegistry.standard(), scheme)
                .paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0));
        graphics.dispose();
        return image;
    }

    /** A pixel on the top border of the tag of the tunnel at {@code position}, half way along. */
    private static Color tagBorder(BufferedImage image, GridPoint position) {
        return new Color(image.getRGB(position.x() * UNIT + 3 * UNIT, position.y() * UNIT + 1));
    }

    @Test
    void aSalidaTakesTheColourOfItsEntrada() {
        GridPoint entrance = new GridPoint(2, 2);
        GridPoint exit = new GridPoint(2, 8);
        add(TunnelKind.ENTRANCE, entrance, Map.of(Tunnels.NAME, "CLK", Tunnels.COLOR, 3));
        add(TunnelKind.EXIT, exit, Map.of(Tunnels.NAME, "CLK"));

        for (ColorScheme scheme : List.of(ColorScheme.LIGHT, ColorSchemes.DARK)) {
            BufferedImage image = render(scheme, Map.of());

            assertThat(tagBorder(image, entrance)).isEqualTo(scheme.tunnelColor(3));
            assertThat(tagBorder(image, exit)).isEqualTo(scheme.tunnelColor(3));
        }
        assertThat(ColorSchemes.DARK.tunnelColor(3)).isNotEqualTo(ColorScheme.LIGHT.tunnelColor(3));
    }

    @Test
    void aSalidaWithoutEntradaIsGrey() {
        GridPoint exit = new GridPoint(2, 8);
        add(TunnelKind.EXIT, exit, Map.of(Tunnels.NAME, "LOST"));

        BufferedImage image = render(ColorScheme.LIGHT, Map.of());

        assertThat(tagBorder(image, exit)).isEqualTo(ColorScheme.LIGHT.tunnelColor(Tunnels.NO_COLOR));
        assertThat(ColorScheme.LIGHT.tunnelColor(Tunnels.NO_COLOR)).isNotIn(paletteColours(ColorScheme.LIGHT));
    }

    @Test
    void theStubIsDrawnInTheColourOfItsNetAndThickWhenTheNetIsABus() {
        ComponentType constantType = registry.require(StandardComponentTypes.CONSTANT);
        Component constant = constantType.newComponent(document.newComponentId(), new GridPoint(2, 2), Rotation.R0,
                Map.of(), "");
        document.add(constant);
        GridPoint output = constantType.pinLocation(constant, 0);
        GridPoint pin = new GridPoint(output.x() + 3, output.y());
        document.add(new Wire(document.newWireId(), output, pin));
        add(TunnelKind.ENTRANCE, new GridPoint(pin.x(), pin.y() - 1), Map.of(Tunnels.NAME, "BUS"));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        String netId = netlist.netForPin(new PinRef(constant.id(), 0)).orElseThrow().id().value();
        Map<String, Value> values = new HashMap<>();
        values.put(netId, Value.of(8, 1));

        BufferedImage image = render(ColorScheme.LIGHT, values);

        // two pixels off the stub's middle line: inside a bus stroke (4.5 px), outside a 1-bit one (2.5 px)
        Color besideTheStub = new Color(image.getRGB(pin.x() * UNIT + 4, pin.y() * UNIT + 1));
        assertThat(besideTheStub).isEqualTo(ColorScheme.LIGHT.wireOne());
    }

    @Test
    void theGhostOfASalidaTakesTheColourItIsGivenForItsName() {
        GridPoint position = new GridPoint(2, 2);
        Component exit = tunnel(TunnelKind.EXIT, position, Map.of(Tunnels.NAME, "CLK"));
        Component entranceInColourThree = tunnel(TunnelKind.ENTRANCE, position,
                Map.of(Tunnels.NAME, "CLK", Tunnels.COLOR, 3));
        Component entranceInColourZero = tunnel(TunnelKind.ENTRANCE, position,
                Map.of(Tunnels.NAME, "CLK", Tunnels.COLOR, 0));

        for (ColorScheme scheme : List.of(ColorScheme.LIGHT, ColorSchemes.DARK)) {
            Color joined = tagBorder(ghost(scheme, exit, Map.of("CLK", 3)), position);
            Color alone = tagBorder(ghost(scheme, exit, Map.of("OTHER", 3)), position);

            assertThat(joined).isEqualTo(tagBorder(ghost(scheme, entranceInColourThree, Map.of()), position));
            assertThat(alone).isEqualTo(tagBorder(ghost(scheme, entranceInColourZero, Map.of()), position));
            assertThat(joined).isNotEqualTo(alone);
        }
    }

    @Test
    void theGhostOfAnEntradaKeepsItsOwnColour() {
        GridPoint position = new GridPoint(2, 2);
        Component entrance = tunnel(TunnelKind.ENTRANCE, position, Map.of(Tunnels.NAME, "CLK", Tunnels.COLOR, 3));

        assertThat(tagBorder(ghost(ColorScheme.LIGHT, entrance, Map.of("CLK", 5)), position))
                .isEqualTo(tagBorder(ghost(ColorScheme.LIGHT, entrance, Map.of()), position));
    }

    @Test
    void aGhostWithoutColoursIsTheContextFreeDrawing() {
        GridPoint position = new GridPoint(2, 2);
        Component exit = tunnel(TunnelKind.EXIT, position, Map.of(Tunnels.NAME, "CLK"));
        CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(), ColorScheme.LIGHT);
        BufferedImage image = blank(ColorScheme.LIGHT);
        Graphics2D graphics = image.createGraphics();
        renderer.paintGhost(graphics, exit);
        graphics.dispose();

        assertThat(tagBorder(image, position)).isEqualTo(tagBorder(ghost(ColorScheme.LIGHT, exit, Map.of()), position));
    }

    /** A tunnel that is not in the document, like one being placed or pasted. */
    private Component tunnel(TunnelKind kind, GridPoint position, Map<String, Object> parameters) {
        return registry.require(kind.typeId()).newComponent(new ElementId("ghost"), position, Rotation.R0,
                parameters, "");
    }

    private BufferedImage ghost(ColorScheme scheme, Component component, Map<String, Integer> tunnelColors) {
        BufferedImage image = blank(scheme);
        Graphics2D graphics = image.createGraphics();
        new CircuitRenderer(registry, PainterRegistry.standard(), scheme)
                .paintGhost(graphics, component, registry, tunnelColors);
        graphics.dispose();
        return image;
    }

    private static BufferedImage blank(ColorScheme scheme) {
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        graphics.dispose();
        return image;
    }

    private static List<Color> paletteColours(ColorScheme scheme) {
        return IntStream.range(0, Tunnels.COLOR_COUNT).mapToObj(scheme::tunnelColor).toList();
    }
}
