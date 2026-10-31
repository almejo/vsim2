package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canvas animates a Terminal (Automan's star, blink, glow, persistence) on the renderer's clock; an export or a
 * printed page shows a still screen without effects, whatever the time, and a still view (reduced motion, an
 * inactive window) moves nothing.
 */
class CircuitRendererTerminalTest {

    private static final long MILLISECOND = 1_000_000L;
    private static final int IMAGE_SIZE = 500;
    private static final Map<String, Object> PARAMETERS = Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20,
            TerminalTypes.CURSOR, "automan", TerminalTypes.EFFECT, "strong");

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(),
            ColorScheme.LIGHT);
    private long now = 5_000 * MILLISECOND;
    private final Component terminal;
    private final VisibleState.TerminalState state;

    CircuitRendererTerminalTest() {
        terminal = registry.require(StandardComponentTypes.TERMINAL).newComponent(document.newComponentId(),
                new GridPoint(4, 4), Rotation.R0, PARAMETERS, "");
        document.add(terminal);
        renderer.useTerminalClock(() -> now);
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(4, 20));
        for (byte value : "hola".getBytes(StandardCharsets.UTF_8)) {
            screen.write(value & 0xFF, 0);
        }
        state = new VisibleState.TerminalState(1, screen.version(), screen.frame(), false);
    }

    private BufferedImage frame(PaintPolicy policy) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 10, true, Map.of(),
                Map.of(terminal.id().value(), state), List.of(), Optional.empty());
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
    void onlyTheCanvasAnimatesParts() {
        assertThat(PaintPolicy.CANVAS.animateParts()).isTrue();
        assertThat(PaintPolicy.CANVAS.withBusValues(false).animateParts()).isTrue();
        assertThat(PaintPolicy.IMAGE.animateParts()).isFalse();
        assertThat(PaintPolicy.print(false).animateParts()).isFalse();
        assertThat(PaintPolicy.print(true).atPageScale(2).animateParts()).isFalse();
    }

    @Test
    void theCanvasMovesAutomansStarOverTime() {
        BufferedImage first = frame(PaintPolicy.CANVAS);
        now += 130 * MILLISECOND;
        BufferedImage later = frame(PaintPolicy.CANVAS);
        assertThat(differentPixels(first, later)).isGreaterThan(20);
        assertThat(renderer.terminalMotions()).hasSize(1);
        Rectangle2D area = renderer.terminalMotions().getFirst().documentArea();
        assertThat(area.intersects(4 * 8, 4 * 8, 20 * 8, 10 * 8)).isTrue();
    }

    @Test
    void anExportIsStillDeterministicAndWithoutEffects() {
        BufferedImage first = frame(PaintPolicy.IMAGE);
        now += 130 * MILLISECOND;
        BufferedImage later = frame(PaintPolicy.IMAGE);
        assertThat(differentPixels(first, later)).isZero();
        assertThat(differentPixels(frame(PaintPolicy.print(false)), later)).isZero();
        assertThat(differentPixels(frame(PaintPolicy.CANVAS), later)).as("the canvas has the strong effect")
                .isGreaterThan(500);
    }

    @Test
    void aStillViewMovesNothingAndReportsNoMotion() {
        renderer.useMotionGate(() -> false);
        BufferedImage first = frame(PaintPolicy.CANVAS);
        now += 130 * MILLISECOND;
        BufferedImage later = frame(PaintPolicy.CANVAS);
        assertThat(differentPixels(first, later)).isZero();
        assertThat(renderer.terminalMotions()).isEmpty();
    }

    @Test
    void aTerminalTakenOutOfTheDocumentStopsMoving() {
        frame(PaintPolicy.CANVAS);
        assertThat(renderer.terminalMotions()).isNotEmpty();
        document.remove(terminal.id());
        frame(PaintPolicy.CANVAS);
        assertThat(renderer.terminalMotions()).isEmpty();
    }
}
