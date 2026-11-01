package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.UartTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canvas animates the integrated screen of a UART 16550 as it does a Terminal's (Automan's star, blink, glow,
 * persistence, screen effect, a kept picture of the screen); an export shows it still and without effects, and a UART
 * with TX and RX pins has nothing to animate.
 */
class CircuitRendererUartTest {

    private static final long MILLISECOND = 1_000_000L;
    private static final int IMAGE_SIZE = 700;
    private static final VisibleState.UartRegisters REGISTERS = new VisibleState.UartRegisters(-1, -1, 0, 1, 0, 0,
            0, 0x60, 0xB0, 0, 0, 0);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(),
            ColorScheme.LIGHT);
    private long now = 5_000 * MILLISECOND;
    private Component uart;
    private VisibleState state;

    CircuitRendererUartTest() {
        renderer.useTerminalClock(() -> now);
    }

    private void place(String screen) {
        Map<String, Object> parameters = new HashMap<>(Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20,
                TerminalTypes.CURSOR, "automan", TerminalTypes.EFFECT, "strong"));
        parameters.put(UartTypes.SCREEN, screen);
        uart = registry.require(StandardComponentTypes.UART_16550).newComponent(document.newComponentId(),
                new GridPoint(4, 4), Rotation.R0, parameters, "");
        document.add(uart);
        TerminalScreen terminalScreen = new TerminalScreen(TerminalSettings.vt(4, 20));
        for (byte value : "hola".getBytes(StandardCharsets.UTF_8)) {
            terminalScreen.write(value & 0xFF, 0);
        }
        Optional<VisibleState.TerminalState> shown = UartTypes.SCREEN_INTEGRATED.equals(screen)
                ? Optional.of(new VisibleState.TerminalState(1, terminalScreen.version(), terminalScreen.frame(),
                false))
                : Optional.empty();
        state = new VisibleState.UartState(REGISTERS, 0, shown);
    }

    private BufferedImage frame(PaintPolicy policy) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 10, true, Map.of(),
                Map.of(uart.id().value(), state), List.of(), Optional.empty());
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(2, 2);
        renderer.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0,
                RenderState.NO_PROBLEM_GLOW, Set.of(), policy));
        graphics.dispose();
        return image;
    }

    private static int differentPixels(BufferedImage first, BufferedImage second) {
        int count = 0;
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void theCanvasMovesAutomansStarOnTheIntegratedScreen() {
        place(UartTypes.SCREEN_INTEGRATED);
        BufferedImage first = frame(PaintPolicy.CANVAS);
        now += 130 * MILLISECOND;
        BufferedImage later = frame(PaintPolicy.CANVAS);
        assertThat(differentPixels(first, later)).isGreaterThan(20);
        assertThat(renderer.terminalMotions()).hasSize(1);
        Rectangle2D area = renderer.terminalMotions().getFirst().documentArea();
        double screenLeft = (4 + UartTypes.SCREEN_LEFT_UNITS) * GridPoint.PIXELS_PER_UNIT;
        assertThat(area.getMinX()).as("the moving area is on the screen, right of the register table")
                .isGreaterThanOrEqualTo(screenLeft - GridPoint.PIXELS_PER_UNIT);
    }

    @Test
    void anExportOfTheUartIsStillAndWithoutEffects() {
        place(UartTypes.SCREEN_INTEGRATED);
        BufferedImage first = frame(PaintPolicy.IMAGE);
        now += 130 * MILLISECOND;
        BufferedImage later = frame(PaintPolicy.IMAGE);
        assertThat(differentPixels(first, later)).isZero();
        assertThat(differentPixels(frame(PaintPolicy.CANVAS), later)).as("the canvas has the strong effect")
                .isGreaterThan(500);
        assertThat(renderer.terminalScreenPictures()).isEqualTo(1);
    }

    @Test
    void anExportKeepsNoPictureOfTheUartsScreen() {
        place(UartTypes.SCREEN_INTEGRATED);
        frame(PaintPolicy.IMAGE);
        frame(PaintPolicy.print(false));
        assertThat(renderer.terminalScreenPictures()).isZero();
    }

    @Test
    void aUartWithPinsHasNothingToAnimate() {
        place(UartTypes.SCREEN_PINS);
        frame(PaintPolicy.CANVAS);
        now += 130 * MILLISECOND;
        frame(PaintPolicy.CANVAS);
        assertThat(renderer.terminalMotions()).isEmpty();
        assertThat(renderer.terminalScreenPictures()).isZero();
    }

    @Test
    void aStillViewMovesNothingOnTheUart() {
        place(UartTypes.SCREEN_INTEGRATED);
        renderer.useMotionGate(() -> false);
        BufferedImage first = frame(PaintPolicy.CANVAS);
        now += 130 * MILLISECOND;
        BufferedImage later = frame(PaintPolicy.CANVAS);
        assertThat(differentPixels(first, later)).isZero();
        assertThat(renderer.terminalMotions()).isEmpty();
    }
}
