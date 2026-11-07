package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.LedMatrixGlow;
import cl.almejo.vsim.ui.paint.LedPainter;
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

/** A Matriz LED with «Brillo que se apaga» shows a LED that just went off still partly lit; without it, off. */
class CircuitRendererMatrixAfterglowTest {

    private static final ColorScheme SCHEME = ColorScheme.LIGHT;
    private static final int IMAGE_SIZE = 200;
    /** The only LED of a 1 x 1 matrix at grid (4, 4), at two pixels per document pixel. */
    private static final int LED_X = 96;
    private static final int LED_Y = 80;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(), SCHEME);
    private long now;

    private CircuitDocument documentWith(boolean afterglow, Component[] placed) {
        CircuitDocument document = new CircuitDocument();
        Component matrix = registry.require(StandardComponentTypes.LED_MATRIX).newComponent(document.newComponentId(),
                new GridPoint(4, 4), Rotation.R0,
                Map.of("rows", 1, "columns", 1, "style", "pixel", "afterglow", afterglow), "");
        document.add(matrix);
        placed[0] = matrix;
        return document;
    }

    private Color ledPixel(CircuitDocument document, Component matrix, boolean lit, long time) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        VisibleState state = new VisibleState.LedMatrixState(List.of(Value.of(1, lit ? 1 : 0)));
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), time, true, Map.of(),
                Map.of(matrix.id().value(), state), List.of(), Optional.empty());
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        graphics.scale(2, 2);
        renderer.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0));
        graphics.dispose();
        return new Color(image.getRGB(LED_X, LED_Y));
    }

    @Test
    void aLedThatJustWentOffIsBetweenLitAndOffWhenTheMatrixFades() {
        Component[] placed = new Component[1];
        CircuitDocument document = documentWith(true, placed);
        renderer.useAfterglowClock(() -> now);
        Color lit = ledPixel(document, placed[0], true, 100);
        now += LedMatrixGlow.FADE_NANOS / 2;
        Color fading = ledPixel(document, placed[0], false, 200);
        now += LedMatrixGlow.FADE_NANOS;
        Color off = ledPixel(document, placed[0], false, 300);

        assertThat(lit).isEqualTo(LedPainter.colorFor("red"));
        assertThat(off).isEqualTo(LedPainter.unlitColorFor("red"));
        assertThat(fading).isNotIn(lit, off);
    }

    @Test
    void withoutTheOptionALedGoesOffAtOnce() {
        Component[] placed = new Component[1];
        CircuitDocument document = documentWith(false, placed);
        renderer.useAfterglowClock(() -> now);
        ledPixel(document, placed[0], true, 100);
        now += LedMatrixGlow.FADE_NANOS / 2;
        assertThat(ledPixel(document, placed[0], false, 200)).isEqualTo(LedPainter.unlitColorFor("red"));
    }
}
