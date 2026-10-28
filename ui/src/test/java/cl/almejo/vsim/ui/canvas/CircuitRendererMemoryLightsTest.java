package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.VisibleState.LastRead;
import cl.almejo.vsim.core.component.VisibleState.LastWrite;
import cl.almejo.vsim.core.component.VisibleState.MemoryAccess;
import cl.almejo.vsim.core.component.VisibleState.MemoryState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.paint.ColorRole;
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

/**
 * The canvas lights a memory's RD and WR by what it did since the frame before, not only by its last access: the
 * «RAM que se llena» counter writes and then reads the next address within the same frame.
 */
class CircuitRendererMemoryLightsTest {

    private static final Color READ = new Color(0x00CC11);
    private static final Color WRITE = new Color(0xCC00AA);
    private static final ColorScheme SCHEME = ColorScheme.LIGHT.with(ColorRole.MEMORY_READ, READ)
            .with(ColorRole.MEMORY_WRITE, WRITE);
    private static final int IMAGE_SIZE = 500;
    private static final long MILLISECOND = 1_000_000L;
    private static final List<Long> WINDOW = List.of(0x10L, 0x11L, 0x12L, 0x13L, 0x14L);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(), SCHEME);
    private long now = 5_000 * MILLISECOND;
    private final Component ram;

    CircuitRendererMemoryLightsTest() {
        ram = registry.require(StandardComponentTypes.RAM).newComponent(document.newComponentId(),
                new GridPoint(4, 4), Rotation.R0, Map.of(), "");
        document.add(ram);
        renderer.useMemoryClock(() -> now);
    }

    /** The «RAM que se llena» moment: wrote 0x12 at 2 (write number {@code writes}), then read 3. */
    private static MemoryState wroteThenRead(long reads, long writes) {
        return new MemoryState(true, MemoryAccess.READ, 3, 1, WINDOW, 0, new LastRead(reads, writes * 1000 + 3, 3),
                new LastWrite(writes, writes * 1000, 2, 0, WINDOW, 0));
    }

    private BufferedImage frame(VisibleState state, long time) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), time, true, Map.of(),
                Map.of(ram.id().value(), state), List.of(), Optional.empty());
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        graphics.scale(2, 2);
        renderer.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0));
        graphics.dispose();
        return image;
    }

    private static int countPixelsOf(BufferedImage image, Color color) {
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getRGB(x, y) == color.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void aWriteFollowedByAReadInTheSameFrameLightsBothAndHoldsThemThenTheyGoOut() {
        frame(wroteThenRead(1, 1), 1100);
        BufferedImage both = frame(wroteThenRead(2, 2), 2100);
        assertThat(countPixelsOf(both, WRITE)).as("the write is not hidden by the read after it").isGreaterThan(50);
        assertThat(countPixelsOf(both, READ)).as("and the read shows too").isGreaterThan(50);

        now += 100 * MILLISECOND;
        BufferedImage held = frame(wroteThenRead(2, 2), 2200);
        assertThat(countPixelsOf(held, WRITE)).as("held 200 ms").isGreaterThan(50);

        now += 150 * MILLISECOND;
        BufferedImage out = frame(wroteThenRead(2, 2), 2300);
        assertThat(countPixelsOf(out, WRITE)).as("nothing new after 250 ms").isZero();
        assertThat(countPixelsOf(out, READ)).isZero();
    }

    @Test
    void whilePausedTheLightsOfTheLastStepStayOn() {
        frame(wroteThenRead(1, 1), 1100);
        frame(wroteThenRead(2, 2), 2100);
        now += 10_000 * MILLISECOND;
        BufferedImage paused = frame(wroteThenRead(2, 2), 2100);
        assertThat(countPixelsOf(paused, WRITE)).isGreaterThan(50);
        assertThat(countPixelsOf(paused, READ)).isGreaterThan(50);
    }
}
