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

/** The canvas paints the capture look on the Teclado that has the keyboard, and only on the canvas. */
class CircuitRendererKeyboardCaptureTest {

    private static final ColorScheme SCHEME = ColorScheme.LIGHT;
    private static final int SIZE = 400;
    /** A title-bar point with no icon and no text: the part is at (40, 40) px. */
    private static final int PROBE_X = 40 + 80;
    private static final int PROBE_Y = 40 + 3;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(), SCHEME);
    private final Component keyboard;

    CircuitRendererKeyboardCaptureTest() {
        keyboard = registry.require(StandardComponentTypes.KEYBOARD).newComponent(document.newComponentId(),
                new GridPoint(5, 5), Rotation.R0, Map.of(), "");
        document.add(keyboard);
    }

    private int probe(Optional<cl.almejo.vsim.core.document.ElementId> capturing, PaintPolicy policy) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        VisibleState state = new VisibleState.KeyboardState(1, List.of(104), 1, 1);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(),
                Map.of(keyboard.id().value(), state), List.of(), Optional.empty());
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
    void theCapturedKeyboardShowsTheCaptureLookAndAnotherPartDoesNot() {
        assertThat(probe(Optional.of(keyboard.id()), PaintPolicy.CANVAS)).isEqualTo(SCHEME.selection().getRGB());
        assertThat(probe(Optional.empty(), PaintPolicy.CANVAS)).isEqualTo(SCHEME.componentShade().getRGB());
        assertThat(probe(Optional.of(new cl.almejo.vsim.core.document.ElementId("other")), PaintPolicy.CANVAS))
                .isEqualTo(SCHEME.componentShade().getRGB());
    }

    @Test
    void anExportOrPrintNeverShowsTheCaptureLook() {
        // the export and print paths build their frame without a capturing part (the 9-argument constructor)
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(),
                Map.of(keyboard.id().value(), new VisibleState.KeyboardState(1, List.of(104), 1, 1)), List.of(),
                Optional.empty());
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        renderer.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0,
                RenderState.NO_PROBLEM_GLOW, Set.of(), PaintPolicy.IMAGE));
        graphics.dispose();

        assertThat(image.getRGB(PROBE_X, PROBE_Y)).isEqualTo(SCHEME.componentShade().getRGB());
    }
}
