package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.canvas.RenderState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NetHaloTest {

    /** Document pixels per grid unit. */
    private static final int UNIT = 8;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void closeTheDocument() {
        openDocument.close();
    }

    @Test
    void theHaloSurroundsEveryWireOfTheNetAndLeavesTheWireItselfToBeRead() {
        NetCardCircuits.AndExample example = NetCardCircuits.andExample(openDocument);
        NetSummary summary = NetSummaries.forWire(openDocument, example.andOutput().id(), SimulationSnapshot.EMPTY)
                .orElseThrow();

        Area halo = NetHalo.area(summary, openDocument.document());

        // the AND's output wire runs from (30, 10) to (35, 10); the NOT's input wire from (35, 20) to (40, 20)
        assertThat(halo.contains(32 * UNIT, 10 * UNIT - 4)).isTrue();
        assertThat(halo.contains(38 * UNIT, 20 * UNIT + 4)).isTrue();
        assertThat(halo.contains(32 * UNIT, 10 * UNIT)).isFalse();
        // the wire from switch A belongs to another net
        double inputY = example.inputA().start().y() * UNIT;
        assertThat(halo.contains((example.inputA().start().x() + 3) * UNIT, inputY - 4)).isFalse();
    }

    @Test
    void nothingIsPaintedWithoutAHoveredNet() {
        NetCardCircuits.andExample(openDocument);
        NetHalo halo = new NetHalo(Optional::empty);
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        halo.paint(graphics, state());
        graphics.dispose();

        assertThat(image.getRGB(32 * UNIT, 10 * UNIT - 4) >>> 24).isZero();
    }

    @Test
    void aHoveredNetIsPaintedAroundItsWire() {
        NetCardCircuits.AndExample example = NetCardCircuits.andExample(openDocument);
        NetSummary summary = NetSummaries.forWire(openDocument, example.andOutput().id(), SimulationSnapshot.EMPTY)
                .orElseThrow();
        NetHalo halo = new NetHalo(() -> Optional.of(new NetHalo.Shown(summary, 1f)));
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        halo.paint(graphics, state());
        graphics.dispose();

        assertThat(image.getRGB(32 * UNIT, 10 * UNIT - 4) >>> 24).isPositive();
        assertThat(image.getRGB(32 * UNIT, 10 * UNIT) >>> 24).isZero();
    }

    private RenderState state() {
        return new RenderState(openDocument.document(), openDocument.netlist(), openDocument.netlistToken(),
                SimulationSnapshot.EMPTY, Set.of(), 0, RenderState.NO_PROBLEM_GLOW, Set.of(), PaintPolicy.CANVAS,
                Optional.empty(), Map.of(), Map.of());
    }
}
