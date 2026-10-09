package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NetBlinkTest {

    private final CircuitDocument document = new CircuitDocument();
    private final Wire wire = new Wire(document.newWireId(), new GridPoint(4, 1), new GridPoint(4, 7));
    private final AtomicInteger repaints = new AtomicInteger();

    private int pixelOnTheWire(NetBlink blink) {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        blink.paint(graphics, new RenderState(document, Netlist.EMPTY, 0, SimulationSnapshot.EMPTY, Set.of(), 0));
        graphics.dispose();
        return image.getRGB(32, 32);
    }

    @Test
    void blinksTheWiresForSixStepsAndThenStops() throws Exception {
        document.add(wire);
        SwingUtilities.invokeAndWait(() -> {
            NetBlink blink = new NetBlink(ColorScheme.LIGHT, repaints::incrementAndGet);
            assertThat(blink.isBlinking()).isFalse();
            assertThat(pixelOnTheWire(blink)).isZero();

            blink.start(Set.of(wire.id()));
            assertThat(blink.isBlinking()).isTrue();
            assertThat(blink.isLit()).isTrue();
            assertThat(pixelOnTheWire(blink)).isNotZero();

            blink.toggle();
            assertThat(blink.isLit()).isFalse();
            assertThat(pixelOnTheWire(blink)).isZero();

            for (int step = 1; step < NetBlink.TOGGLES; step++) {
                blink.toggle();
            }
            assertThat(blink.isBlinking()).isFalse();
            assertThat(blink.isLit()).isFalse();
            assertThat(pixelOnTheWire(blink)).isZero();
            assertThat(repaints.get()).isEqualTo(1 + NetBlink.TOGGLES);
            blink.dispose();
        });
    }

    @Test
    void startingAgainRestartsTheBlink() throws Exception {
        document.add(wire);
        SwingUtilities.invokeAndWait(() -> {
            NetBlink blink = new NetBlink(ColorScheme.LIGHT, repaints::incrementAndGet);
            blink.start(Set.of(wire.id()));
            blink.toggle();
            blink.toggle();
            blink.start(Set.of(wire.id()));
            for (int step = 1; step < NetBlink.TOGGLES; step++) {
                blink.toggle();
            }
            assertThat(blink.isBlinking()).isTrue();
            blink.dispose();
        });
    }
}
