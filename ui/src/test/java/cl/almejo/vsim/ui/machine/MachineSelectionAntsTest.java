package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineNote;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.MarchingAnts;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** The machine diagram's selection is outlined as the circuit's: the same marching ants, white underlay and clock. */
class MachineSelectionAntsTest {

    private static final int OFFSET_X = 200;
    private static final int OFFSET_Y = 250;
    private final ColorScheme dark = ColorSchemes.DARK;
    private final AtomicBoolean animations = new AtomicBoolean(true);

    private OpenDocument document;
    private OpenDocument place;
    private MachineCanvas canvas;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        document = MachineFixtures.circuitWithMachines(1);
        place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).get(0)));
        canvas = new MachineCanvas(place, MachineFixtures.DEFINITION_ID, new MachineSelection(),
                (snapshot, instancePath) -> Optional.empty(), () -> ColorScheme.LIGHT);
        canvas.setSize(800, 400);
        canvas.setAnimations(animations::get);
        canvas.overrideOnScreen(() -> true);
    }

    @AfterEach
    void close() {
        canvas.dispose();
        place.close();
        document.close();
    }

    @Test
    void aSelectedStateIsOutlinedWithAWhiteUnderlayUnderTheSelectionColour() {
        StateMachine machine = MachineFixtures.detector();
        MachineSelection selection = new MachineSelection();
        selection.selectState("s1");
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);
        BufferedImage image = paint(machine, selection, 0);

        boolean white = false;
        boolean coloured = false;
        Point2D centre = geometry.centre("s1");
        double radius = geometry.radius("s1");
        for (int step = 0; step < 720; step++) {
            double angle = Math.toRadians(step / 2.0);
            Color pixel = new Color(image.getRGB((int) Math.round(OFFSET_X + centre.getX() + radius * Math.cos(angle)),
                    (int) Math.round(OFFSET_Y + centre.getY() + radius * Math.sin(angle))));
            white |= pixel.getRed() > 245 && pixel.getGreen() > 245 && pixel.getBlue() > 245;
            coloured |= distance(pixel, dark.selection()) < 40;
        }
        assertThat(white).as("the white line the dashes lie on").isTrue();
        assertThat(coloured).as("the dashes in the selection colour").isTrue();
    }

    @Test
    void theDashesMoveWithThePhaseAndComeBackAfterOnePeriod() {
        StateMachine machine = MachineFixtures.detector();
        MachineSelection selection = new MachineSelection();
        selection.selectState("s1");

        assertThat(pixels(paint(machine, selection, 0))).isNotEqualTo(pixels(paint(machine, selection, 3)));
        assertThat(pixels(paint(machine, selection, 0)))
                .isEqualTo(pixels(paint(machine, selection, MarchingAnts.PERIOD)));
    }

    @Test
    void aSelectedNoteMarchesLikeTheRestAndComesBackAfterOnePeriod() {
        MachineNote note = new MachineNote("n1", new DiagramPoint(40, 150), MachineNote.DEFAULT_WIDTH,
                MachineNote.DEFAULT_HEIGHT, StickyColor.YELLOW, "hola");

        assertThat(pixels(paintNoteRing(note, 0))).isNotEqualTo(pixels(paintNoteRing(note, 3)));
        assertThat(pixels(paintNoteRing(note, 0)))
                .isEqualTo(pixels(paintNoteRing(note, MarchingAnts.PERIOD)));
    }

    @Test
    void theAntsMarchOnlyWhileSomethingIsSelectedAndAnimationsAreOn() {
        assertThat(canvas.antsRunning()).as("nothing selected: no clock, no repaints").isFalse();

        canvas.selection().selectState("s1");
        assertThat(canvas.antsRunning()).isTrue();
        double phase = canvas.antsPhase();
        canvas.antsTick();
        assertThat(canvas.antsPhase()).isNotEqualTo(phase);

        animations.set(false);
        canvas.animationsChanged();
        assertThat(canvas.antsRunning()).as("«Animaciones: No»: dashed and still").isFalse();

        animations.set(true);
        canvas.animationsChanged();
        assertThat(canvas.antsRunning()).isTrue();

        canvas.selection().clear();
        assertThat(canvas.antsRunning()).as("selection gone").isFalse();
    }

    @Test
    void aRubberBandMakesTheAntsMarchToo() {
        canvas.showGestures(new MachineCanvas.Gestures() {
            @Override
            public Optional<StateMachine> preview() {
                return Optional.empty();
            }

            @Override
            public MachineDiagramPainter.Overlay overlay() {
                return new MachineDiagramPainter.Overlay(Optional.empty(), Optional.empty(),
                        Optional.of(new Rectangle2D.Double(0, 0, 50, 50)), false);
            }
        });

        assertThat(canvas.antsRunning()).isTrue();
        canvas.showGestures(MachineCanvas.Gestures.NONE);
        assertThat(canvas.antsRunning()).isFalse();
    }

    private BufferedImage paint(StateMachine machine, MachineSelection selection, double phase) {
        BufferedImage image = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(dark.background());
        graphics.fillRect(0, 0, 800, 500);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.translate(OFFSET_X, OFFSET_Y);
        MachineDiagramPainter.paint(graphics, machine, MachineDiagramGeometry.of(machine),
                new MachineDiagramPainter.Marks(Optional.empty(), selection, MachineGlows.NONE, Optional.empty(),
                        phase), dark);
        graphics.dispose();
        return image;
    }

    private BufferedImage paintNoteRing(MachineNote note, double phase) {
        BufferedImage image = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(dark.background());
        graphics.fillRect(0, 0, 800, 500);
        graphics.translate(OFFSET_X, OFFSET_Y);
        MachineNotePainting.paintSelection(graphics, note, dark, phase);
        graphics.dispose();
        return image;
    }

    private static List<Integer> pixels(BufferedImage image) {
        return Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
                .boxed().toList();
    }

    private static double distance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }
}
