package cl.almejo.vsim.ui.sticky;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.canvas.PaintPolicy;
import cl.almejo.vsim.ui.canvas.RenderState;
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

/** Sticky notes in the renderer: a last pass over everything, left out when the policy says so. */
class StickyNotesOnTheCanvasTest {

    private static final ColorScheme SCHEME = ColorScheme.LIGHT;
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitRenderer renderer = new CircuitRenderer(registry, PainterRegistry.standard(), SCHEME);
    private final CircuitDocument document = new CircuitDocument();

    private BufferedImage render(PaintPolicy policy) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationSnapshot snapshot = new SimulationSnapshot(1, document.revision(), 0, true, Map.of(), Map.of(),
                List.of(), Optional.empty());
        BufferedImage image = new BufferedImage(120, 90, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(SCHEME.background());
        graphics.fillRect(0, 0, 120, 90);
        renderer.paint(graphics, new RenderState(document, netlist, document.revision(), snapshot, Set.of(), 0,
                RenderState.NO_PROBLEM_GLOW, Set.of(), policy));
        graphics.dispose();
        return image;
    }

    private StickyNote stick(int x, int y) {
        StickyNote note = new StickyNote(document.newStickyNoteId(), new GridPoint(x, y), 8, 6, StickyColor.YELLOW,
                new StickyText(""), false);
        document.add(note);
        return note;
    }

    @Test
    void aStickyNoteIsPaintedOverAWireUnderIt() {
        document.add(new Wire(document.newWireId(), new GridPoint(0, 4), new GridPoint(14, 4)));
        stick(2, 2);
        BufferedImage image = render(PaintPolicy.CANVAS);
        // the wire runs through y = 32 px; inside the paper (x 16..80) the paper hides it
        assertThat(new Color(image.getRGB(40, 32), true)).isEqualTo(SCHEME.stickyPaper(StickyColor.YELLOW));
        // outside the paper the wire is still there
        assertThat(new Color(image.getRGB(100, 32), true)).isNotEqualTo(SCHEME.background());
    }

    @Test
    void aPolicyThatLeavesStickyNotesOutPaintsNothingOfThem() {
        stick(2, 2);
        BufferedImage without = render(PaintPolicy.CANVAS.withStickyNotes(false));
        assertThat(new Color(without.getRGB(40, 32), true)).isEqualTo(SCHEME.background());
        BufferedImage with = render(PaintPolicy.CANVAS);
        assertThat(new Color(with.getRGB(40, 32), true)).isEqualTo(SCHEME.stickyPaper(StickyColor.YELLOW));
    }

    @Test
    void thePaperPolicyLeavesThemOutByDefault() {
        assertThat(PaintPolicy.CANVAS.paintStickyNotes()).isTrue();
        assertThat(PaintPolicy.print(false).paintStickyNotes()).isFalse();
        assertThat(PaintPolicy.print(false).withStickyNotes(true).paintStickyNotes()).isTrue();
        assertThat(PaintPolicy.print(true).atPageScale(2).paintStickyNotes()).isFalse();
    }

    @Test
    void theGhostIsPaintedWhereTheNoteWillBe() {
        StickyNote note = new StickyNote(document.newStickyNoteId(), new GridPoint(2, 2), 8, 6, StickyColor.BLUE,
                new StickyText("hola"), false);
        BufferedImage image = new BufferedImage(120, 90, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        renderer.paintGhost(graphics, note);
        graphics.dispose();
        boolean painted = false;
        for (int y = 0; y < image.getHeight() && !painted; y++) {
            for (int x = 0; x < image.getWidth() && !painted; x++) {
                painted = (image.getRGB(x, y) >>> 24) != 0;
            }
        }
        assertThat(painted).isTrue();
    }

    @Test
    void drawnBoundsOfANoteAreItsVisibleSize() {
        StickyNote note = stick(3, 4);
        assertThat(DrawnBounds.of(note, registry)).isEqualTo(new cl.almejo.vsim.core.geometry.Bounds(3, 4, 8, 6));
        assertThat(DrawnBounds.of(note.withFolded(true), registry))
                .isEqualTo(new cl.almejo.vsim.core.geometry.Bounds(3, 4, 2, 2));
    }
}
