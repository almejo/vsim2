package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/** The diagram canvas of a machine tab: where states and arrows go, how the active state shows, zoom and pan. */
class MachineCanvasTest {

    private final AtomicReference<Optional<ActiveMachineState>> shownState = new AtomicReference<>(Optional.empty());
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
                (snapshot, instancePath) -> shownState.get(), () -> ColorScheme.LIGHT);
        canvas.setSize(800, 400);
    }

    @AfterEach
    void close() {
        canvas.dispose();
        place.close();
        document.close();
    }

    @Test
    void twoArrowsJoiningTheSameStatesCurveToEachSideAndTheOthersAreStraightOrLoops() {
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(MachineFixtures.detector());

        assertThat(geometry.arrow("t1").kind()).isEqualTo(MachineDiagramGeometry.ArrowKind.CURVED);
        assertThat(geometry.arrow("t2").kind()).isEqualTo(MachineDiagramGeometry.ArrowKind.CURVED);
        assertThat(geometry.arrow("t1").labelAnchor().getY()).isNotEqualTo(geometry.arrow("t2").labelAnchor().getY());
        assertThat(geometry.arrow("t3").kind()).isEqualTo(MachineDiagramGeometry.ArrowKind.STRAIGHT);
        // S2 -> S0 would pass behind S1: it bends around it (MachineArrowsAroundStatesTest)
        assertThat(geometry.arrow("t5").kind()).isEqualTo(MachineDiagramGeometry.ArrowKind.CURVED);
        assertThat(geometry.arrow("t4").kind()).isEqualTo(MachineDiagramGeometry.ArrowKind.SELF_LOOP);
    }

    @Test
    void anArrowStartsAndEndsOnTheBorderOfItsStates() {
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(MachineFixtures.detector());

        MachineDiagramGeometry.Arrow straight = geometry.arrow("t3"); // S1 (160, 0) → S2 (320, 0)

        assertThat(straight.start().getX()).isCloseTo(160 + MachineDiagramGeometry.STATE_RADIUS,
                offset(0.5));
        assertThat(straight.end().getX()).isCloseTo(320 - MachineDiagramGeometry.STATE_RADIUS, offset(0.5));
    }

    @Test
    void aPointInsideAStateFindsIt() {
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(MachineFixtures.detector());

        assertThat(geometry.stateAt(new Point2D.Double(165, 10))).contains("s1");
        assertThat(geometry.stateAt(new Point2D.Double(80, 80))).isEmpty();
    }

    @Test
    void theActiveStateIsPaintedInTheActiveColour() {
        shownState.set(Optional.of(new ActiveMachineState("s1", Optional.empty(), Optional.empty())));
        canvas.showLatestState();

        BufferedImage image = paint();

        Point2D activeInside = insideLeftEdgeOf(new DiagramPoint(160, 0));
        Point2D idleInside = insideLeftEdgeOf(new DiagramPoint(320, 0));
        Color expected = MachineColors.activeFill(ColorScheme.LIGHT);
        assertThat(new Color(image.getRGB((int) activeInside.getX(), (int) activeInside.getY())))
                .isEqualTo(expected);
        assertThat(new Color(image.getRGB((int) idleInside.getX(), (int) idleInside.getY())))
                .isNotEqualTo(expected);
    }

    @Test
    void withoutAnActiveStateNoStateIsPaintedActive() {
        BufferedImage image = paint();

        for (DiagramPoint centre : List.of(new DiagramPoint(0, 0), new DiagramPoint(160, 0),
                new DiagramPoint(320, 0))) {
            Point2D inside = insideLeftEdgeOf(centre);
            assertThat(new Color(image.getRGB((int) inside.getX(), (int) inside.getY())))
                    .isNotEqualTo(MachineColors.activeFill(ColorScheme.LIGHT));
        }
    }

    @Test
    void theDiagramShowsTheMachineAsItIsNow() {
        StateMachine shown = canvas.machine().orElseThrow();

        assertThat(shown.states()).extracting(state -> state.name()).containsExactly("S0", "S1", "S2");
    }

    @Test
    void controlAndTheWheelZoomAndTheWheelAlonePans() {
        double zoomBefore = canvas.viewport().zoom();
        Point2D originBefore = canvas.viewport().gridToScreen(0, 0);

        wheel(InputEvent.CTRL_DOWN_MASK, -1);
        assertThat(canvas.viewport().zoom()).isGreaterThan(zoomBefore);

        double zoomAfter = canvas.viewport().zoom();
        wheel(0, 1);
        assertThat(canvas.viewport().zoom()).isEqualTo(zoomAfter);
        assertThat(canvas.viewport().gridToScreen(0, 0).getY()).isNotEqualTo(originBefore.getY());
    }

    /**
     * A touchpad's sideways swipe reaches Swing on Linux as presses of mouse buttons 4 (left) and 5 (right), not as a
     * wheel turn: the diagram scrolls sideways as far as a Mayús+wheel step, as the circuit canvas does (hand test of
     * hito 3, item 6), and Mayús+wheel scrolls sideways too.
     */
    @Test
    void sidewaysScrollButtonsAndShiftWheelPanHorizontallyAsOnTheCircuit() {
        Point2D origin = canvas.viewport().gridToScreen(0, 0);
        wheel(InputEvent.SHIFT_DOWN_MASK, -1);
        double wheelStep = canvas.viewport().gridToScreen(0, 0).getX() - origin.getX();
        assertThat(wheelStep).isPositive();

        press(4);
        assertThat(canvas.viewport().gridToScreen(0, 0).getX() - origin.getX()).isCloseTo(2 * wheelStep,
                offset(1e-9));
        press(5);
        press(5);
        assertThat(canvas.viewport().gridToScreen(0, 0).getX()).isCloseTo(origin.getX(), offset(1e-9));
        assertThat(canvas.viewport().gridToScreen(0, 0).getY()).isEqualTo(origin.getY());
    }

    /** A press of extra mouse button {@code button}, built as button 1 (a headless toolkit refuses others). */
    private void press(int button) {
        MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, 10, 10, 1, false,
                MouseEvent.BUTTON1) {
            @Override
            public int getButton() {
                return button;
            }
        };
        for (java.awt.event.MouseListener listener : canvas.getMouseListeners()) {
            listener.mousePressed(event);
        }
    }

    @Test
    void zoomToFitShowsEveryState() {
        canvas.zoomToFit();

        for (DiagramPoint centre : List.of(new DiagramPoint(0, 0), new DiagramPoint(320, 0))) {
            Point2D onScreen = canvas.toScreen(centre);
            assertThat(onScreen.getX()).isBetween(0.0, 800.0);
            assertThat(onScreen.getY()).isBetween(0.0, 400.0);
        }
    }

    private void wheel(int modifiers, int rotation) {
        MouseWheelEvent event = new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 0, modifiers, 400, 200, 0,
                false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
        for (MouseWheelListener listener : canvas.getMouseWheelListeners()) {
            listener.mouseWheelMoved(event);
        }
    }

    private Point2D insideLeftEdgeOf(DiagramPoint centre) {
        Point2D onScreen = canvas.toScreen(centre);
        double inset = (MachineDiagramGeometry.STATE_RADIUS - 8) * canvas.viewport().zoom();
        return new Point2D.Double(onScreen.getX() - inset, onScreen.getY());
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
