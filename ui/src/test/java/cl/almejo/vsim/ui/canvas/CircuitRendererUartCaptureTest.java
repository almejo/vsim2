package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The canvas paints the capture look on an integrated UART 16550 that has the keyboard, and only on the canvas. */
class CircuitRendererUartCaptureTest {

    private static final ColorScheme SCHEME = ColorScheme.LIGHT;
    private static final int SIZE = 400;
    /** A title-bar point between the name and the chip, above both: the part is at (40, 40) px. */
    private static final int PROBE_X = 40 + 80;
    private static final int PROBE_Y = 40 + 3;
    private static final VisibleState.UartRegisters REGISTERS = new VisibleState.UartRegisters(-1, -1, 0, 1, 0, 0,
            0, 0x60, 0xB0, 0, 0, 0);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(), SCHEME);
    private final Component uart;

    CircuitRendererUartCaptureTest() {
        uart = registry.require(StandardComponentTypes.UART_16550).newComponent(document.newComponentId(),
                new GridPoint(5, 5), Rotation.R0, Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20), "");
        document.add(uart);
    }

    private int probe(Optional<ElementId> capturing, PaintPolicy policy) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(),
                Map.of(uart.id().value(), new VisibleState.UartState(REGISTERS, 0, Optional.empty())), List.of(),
                Optional.empty());
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, SIZE, SIZE);
        renderer.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0,
                RenderState.NO_PROBLEM_GLOW, Set.of(), policy, capturing));
        graphics.dispose();
        return image.getRGB(PROBE_X, PROBE_Y);
    }

    @Test
    void theCapturedUartShowsTheCaptureLookAndAnotherPartDoesNot() {
        assertThat(probe(Optional.of(uart.id()), PaintPolicy.CANVAS)).isEqualTo(SCHEME.selection().getRGB());
        assertThat(probe(Optional.empty(), PaintPolicy.CANVAS)).isEqualTo(SCHEME.componentShade().getRGB());
        assertThat(probe(Optional.of(new ElementId("other")), PaintPolicy.CANVAS))
                .isEqualTo(SCHEME.componentShade().getRGB());
    }
}
