package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** In the bundled state machine examples no arrow's condition touches any arrow (its own or another one). */
class MachineExampleLabelsTest {

    /** How close a label may come to a line: about the line's width, so they do not look stuck together. */
    private static final double MARGIN = 1;
    private static final double FLATNESS = 0.5;

    static Stream<String> exampleIds() {
        return BundledExamples.stateMachineIds().stream();
    }

    @ParameterizedTest
    @MethodSource("exampleIds")
    void noConditionTouchesAnArrow(String exampleId) throws Exception {
        BufferedImage canvas = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        FontMetrics metrics = graphics.getFontMetrics(MachineDiagramPainter.labelFont());
        List<String> touches = new ArrayList<>();
        for (ComponentDefinition definition : BundledExamples.open(exampleId, StandardComponentTypes.registry())
                .definitions()) {
            if (definition.machine().isEmpty()) {
                continue;
            }
            StateMachine machine = definition.machine().orElseThrow();
            MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);
            for (MachineTransition labelled : machine.transitions()) {
                if (MachineDiagramPainter.labelOf(labelled).isEmpty()) {
                    continue;
                }
                Rectangle2D grown = drawnText(MachineDiagramPainter.labelOf(labelled),
                        geometry.arrow(labelled.id()).labelAnchor(), metrics, graphics);
                for (MachineTransition other : machine.transitions()) {
                    if (touches(geometry.arrow(other.id()), grown)) {
                        touches.add(MachineDiagramPainter.labelOf(labelled) + " of " + labelled.id()
                                + " touches arrow " + other.id());
                    }
                }
            }
        }
        graphics.dispose();
        assertThat(touches).as(exampleId).isEmpty();
    }

    /**
     * The ink of {@code text} where the painter draws it (centred as {@code drawCentred} does: its width around the
     * centre, its baseline half of ascent minus descent below), grown by {@link #MARGIN}. Not the click area, which is
     * at least three letters wide, nor the font's ascent, which in a logical font is that of its tallest fallback.
     */
    private static Rectangle2D drawnText(String text, Point2D centre, FontMetrics metrics, Graphics2D graphics) {
        double left = centre.getX() - metrics.stringWidth(text) / 2.0;
        double baseline = centre.getY() + (metrics.getAscent() - metrics.getDescent()) / 2.0;
        Rectangle2D ink = metrics.getFont().createGlyphVector(graphics.getFontRenderContext(), text).getVisualBounds();
        return new Rectangle2D.Double(left + ink.getX() - MARGIN, baseline + ink.getY() - MARGIN,
                ink.getWidth() + 2 * MARGIN, ink.getHeight() + 2 * MARGIN);
    }

    private static boolean touches(MachineDiagramGeometry.Arrow arrow, Rectangle2D area) {
        double[] coordinates = new double[6];
        double lastX = 0;
        double lastY = 0;
        for (PathIterator iterator = arrow.path().getPathIterator(null, FLATNESS); !iterator.isDone();
             iterator.next()) {
            int segment = iterator.currentSegment(coordinates);
            if (segment == PathIterator.SEG_LINETO
                    && area.intersectsLine(new Line2D.Double(lastX, lastY, coordinates[0], coordinates[1]))) {
                return true;
            }
            lastX = coordinates[0];
            lastY = coordinates[1];
        }
        return false;
    }
}
