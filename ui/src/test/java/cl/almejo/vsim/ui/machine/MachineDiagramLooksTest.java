package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineChecks;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.OutputAssignment;
import cl.almejo.vsim.core.machine.StateColor;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * How a machine's diagram looks in the cases the user found hard to read (hand test of hito 2): a selected arrow that
 * is also the next one, a long state name and its outputs, and a warning in the dark theme.
 */
class MachineDiagramLooksTest {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 500;
    /** How near (RGB distance) a pixel must be to a colour to count as that colour. */
    private static final double NEAR = 60;

    private static final List<OutputAssignment> THREE_OUTPUTS = List.of(new OutputAssignment("V", false),
            new OutputAssignment("A", true), new OutputAssignment("R", false));

    private static StateMachine machine(MachineState... states) {
        return new StateMachine(List.of(states), List.of(), Optional.of(states[0].id()), List.of(),
                List.of("V", "A", "R"), false, false, StateMachine.EDGE_FALLING, List.of());
    }

    private static StateMachine twoStatesJoined() {
        return machine(new MachineState("a", "A", new DiagramPoint(150, 250), List.of()),
                new MachineState("b", "B", new DiagramPoint(550, 250), List.of()))
                .withTransitions(List.of(new MachineTransition("t1", "a", "b", "", List.of())));
    }

    @Test
    void aSelectedArrowThatIsAlsoTheNextOneStillShowsTheSelectionColourAlongIt() {
        MachineSelection selection = new MachineSelection();
        selection.set(new LinkedHashSet<>(), new LinkedHashSet<>(Set.of("t1")));
        ActiveMachineState active = new ActiveMachineState("a", Optional.of("b"), Optional.of("t1"));

        BufferedImage image = paint(twoStatesJoined(), Optional.of(active), selection, ColorSchemes.DARK);

        // across the middle of the arrow (y = 250), a few pixels each way
        boolean selectionSeen = false;
        boolean activeSeen = false;
        for (int x = 330; x <= 370; x++) {
            for (int y = 242; y <= 258; y++) {
                Color pixel = new Color(image.getRGB(x, y));
                selectionSeen |= distance(pixel, ColorSchemes.DARK.selection()) < NEAR;
                activeSeen |= distance(pixel, ColorSchemes.DARK.machineActive()) < NEAR;
            }
        }
        assertThat(activeSeen).as("still the next arrow's orange").isTrue();
        assertThat(selectionSeen).as("and the selection's blue beside it").isTrue();
    }

    @Test
    void aStateWithAColourIsFilledWithItAndStillShowsTheActiveRing() {
        StateMachine plain = twoStatesJoined();
        StateMachine coloured = plain.withState("a", state -> state.withColor(Optional.of(StateColor.GREEN)));
        ActiveMachineState active = new ActiveMachineState("a", Optional.empty(), Optional.empty());
        for (ColorScheme scheme : List.of(ColorSchemes.LIGHT, ColorSchemes.DARK)) {
            BufferedImage before = paint(plain, Optional.of(active), new MachineSelection(), scheme);
            BufferedImage after = paint(coloured, Optional.of(active), new MachineSelection(), scheme);

            // above the name, inside the circle (centre 150, 250; radius 40)
            Color inside = new Color(after.getRGB(150, 222));
            assertThat(inside).as("filled: %s", scheme).isEqualTo(scheme.stateFill(StateColor.GREEN))
                    .isNotEqualTo(new Color(before.getRGB(150, 222)));
            assertThat(distance(new Color(after.getRGB(150, 211)), scheme.machineActive()))
                    .as("the active ring on the rim: %s", scheme).isLessThan(NEAR);
        }
    }

    @Test
    void theStartArrowBeginsAtAFilledDotInTheArrowsColourThatScalesWithTheZoom() {
        StateMachine machine = twoStatesJoined();
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);
        Point2D origin = geometry.initialMark().orElseThrow().getP1();
        assertThat(origin).isEqualTo(new Point2D.Double(58, 250));
        assertThat(geometry.bounds().orElseThrow().getMinX()).as("the dot is part of the diagram")
                .isLessThanOrEqualTo(origin.getX() - 3);
        for (ColorScheme scheme : List.of(ColorSchemes.LIGHT, ColorSchemes.DARK)) {
            for (double zoom : new double[]{1, 2}) {
                BufferedImage image = paint(machine, Optional.empty(), new MachineSelection(), MachineGlows.NONE,
                        scheme, zoom);
                int x = (int) Math.round(origin.getX() * zoom);
                int y = (int) Math.round(origin.getY() * zoom);
                int off = (int) Math.round(3 * zoom);
                for (int[] point : new int[][]{{x, y}, {x - off, y}, {x, y - off}, {x, y + off}}) {
                    assertThat(distance(new Color(image.getRGB(point[0], point[1])), MachineColors.muted(scheme)))
                            .as("%s at %s, zoom %s", Arrays.toString(point), scheme, zoom).isLessThan(NEAR);
                }
            }
        }
    }

    @Test
    void aLongNameGrowsItsStateUntilTheNameFitsInside() {
        StateMachine machine = machine(new MachineState("a", "AMARILLO", new DiagramPoint(150, 250), List.of()),
                new MachineState("b", "S1", new DiagramPoint(550, 250), List.of()))
                .withTransitions(List.of(new MachineTransition("t1", "b", "a", "", List.of())));
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);

        assertThat(geometry.radius("a")).isGreaterThan(MachineDiagramGeometry.STATE_RADIUS)
                .isLessThanOrEqualTo(MachineDiagramGeometry.MAXIMUM_RADIUS);
        assertNameInside(geometry, "a");
        assertThat(geometry.radius("b")).isEqualTo(MachineDiagramGeometry.STATE_RADIUS);
        assertThat(geometry.nameLines("b")).containsExactly("S1");
        // the arrow ends on the grown border, not on the name
        assertThat(geometry.arrow("t1").end().distance(geometry.centre("a")))
                .isCloseTo(geometry.radius("a"), offset(0.5));
        assertThat(geometry.stateAt(new Point2D.Double(150 + geometry.radius("a") - 2, 250))).contains("a");
    }

    @Test
    void aLongNameWithSpacesWrapsIntoBalancedLinesInsteadOfAHugeCircle() {
        // hand test of hito 2, round 2: «amarillo jojo (2)» made a very big circle
        StateMachine machine = machine(new MachineState("a", "amarillo jojo (2)", new DiagramPoint(150, 250),
                List.of()));
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);

        assertThat(geometry.nameLines("a")).containsExactly("amarillo", "jojo (2)");
        assertThat(geometry.nameFont("a").getSize2D()).isEqualTo(MachineDiagramPainter.nameFont().getSize2D());
        assertThat(geometry.radius("a")).isLessThanOrEqualTo(MachineDiagramGeometry.MAXIMUM_RADIUS);
        assertNameInside(geometry, "a");
    }

    @Test
    void aNameWrapsIntoAtMostThreeLinesChoosingTheNarrowestWidestLine() {
        StateMachine machine = machine(
                new MachineState("a", "uno dos tres cuatro cinco seis", new DiagramPoint(150, 250), List.of()),
                new MachineState("b", "ab cd efghijklmnop", new DiagramPoint(550, 250), List.of()));
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);

        assertThat(geometry.nameLines("a")).hasSizeLessThanOrEqualTo(3);
        assertThat(String.join(" ", geometry.nameLines("a"))).isEqualTo("uno dos tres cuatro cinco seis");
        // «ab cd» together beside the long word, not «ab» alone
        assertThat(geometry.nameLines("b")).containsExactly("ab cd", "efghijklmnop");
        assertNameInside(geometry, "a");
    }

    @Test
    void pastTheLargestCircleTheNameShrinks() {
        StateMachine machine = machine(new MachineState("a", "SEMAFORONORTESUR", new DiagramPoint(150, 250),
                List.of()));
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);

        assertThat(geometry.nameLines("a")).containsExactly("SEMAFORONORTESUR");
        assertThat(geometry.radius("a")).isEqualTo(MachineDiagramGeometry.MAXIMUM_RADIUS);
        assertThat(MachineDiagramGeometry.MAXIMUM_RADIUS).isCloseTo(1.5 * MachineDiagramGeometry.STATE_RADIUS,
                offset(0.01));
        assertThat(geometry.nameFont("a").getSize2D()).isLessThan(MachineDiagramPainter.nameFont().getSize2D());
        assertNameInside(geometry, "a");
    }

    @Test
    void theOutputsOfAStateWithAWrappedNameGoUnderTheWholeName() {
        StateMachine machine = machine(new MachineState("a", "amarillo jojo", new DiagramPoint(150, 250),
                List.of(new OutputAssignment("A", true))));
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);
        FontMetrics metrics = metrics(geometry.nameFont("a"));
        double nameBottom = 250 + geometry.nameLines("a").size() * (metrics.getAscent() + metrics.getDescent()) / 2.0;

        Rectangle2D outputs = geometry.outputsBounds("a").orElseThrow();

        assertThat(geometry.nameLines("a")).hasSize(2);
        assertThat(outputs.getMinY()).isGreaterThanOrEqualTo(nameBottom);
    }

    /** Every line of the state's name, as the painter stacks them, lies inside its circle. */
    private static void assertNameInside(MachineDiagramGeometry geometry, String stateId) {
        FontMetrics metrics = metrics(geometry.nameFont(stateId));
        List<String> lines = geometry.nameLines(stateId);
        double lineHeight = metrics.getAscent() + metrics.getDescent();
        double top = geometry.centre(stateId).getY() - lines.size() * lineHeight / 2;
        for (int index = 0; index < lines.size(); index++) {
            double width = metrics.stringWidth(lines.get(index));
            assertInside(new Rectangle2D.Double(geometry.centre(stateId).getX() - width / 2,
                    top + index * lineHeight, width, lineHeight), geometry.centre(stateId), geometry.radius(stateId));
        }
    }

    @Test
    void theOutputsLineUnderTheNameStaysInsideTheCircle() {
        StateMachine machine = machine(new MachineState("a", "AMARILLO", new DiagramPoint(150, 250),
                THREE_OUTPUTS));
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);

        Rectangle2D outputs = geometry.outputsBounds("a").orElseThrow();

        assertInside(outputs, geometry.centre("a"), geometry.radius("a"));
    }

    @Test
    void outputsTooLongForTheCircleGoFullyBelowIt() {
        List<OutputAssignment> many = List.of(new OutputAssignment("ROJO_NORTE", true),
                new OutputAssignment("AMARILLO_NORTE", false), new OutputAssignment("VERDE_NORTE", false),
                new OutputAssignment("ROJO_SUR", false), new OutputAssignment("VERDE_SUR", true));
        StateMachine machine = machine(new MachineState("a", "S0", new DiagramPoint(150, 250), many));
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);

        Rectangle2D outputs = geometry.outputsBounds("a").orElseThrow();

        assertThat(outputs.getMinY()).isGreaterThan(250 + geometry.radius("a"));
        assertThat(geometry.bounds().orElseThrow().contains(outputs)).as("«Ver todo» shows them").isTrue();
    }

    @Test
    void aWarningStateHasAnAmberRingAndAClearHaloInTheDarkTheme() {
        StateMachine machine = machine(new MachineState("a", "A", new DiagramPoint(150, 250), List.of()),
                new MachineState("lost", "S0", new DiagramPoint(550, 250), List.of()));
        ColorScheme dark = ColorSchemes.DARK;
        MachineGlows glows = MachineGlows.of(MachineChecks.check(machine));
        assertThat(glows.ofState("lost")).contains(MachineGlows.Glow.WARNING);

        BufferedImage image = paint(machine, Optional.empty(), new MachineSelection(), glows, dark);
        double radius = MachineDiagramGeometry.STATE_RADIUS;
        Color ring = new Color(image.getRGB(550, (int) Math.round(250 + radius)));
        Color halo = new Color(image.getRGB(550, (int) Math.round(250 + radius + 5)));

        assertThat(distance(ring, dark.warning())).as("the circle's own ring is amber")
                .isLessThan(distance(ring, dark.componentStroke()));
        // the halo goes at least two fifths of the way from the board to the amber
        double share = 1 - distance(halo, dark.warning()) / distance(dark.background(), dark.warning());
        assertThat(share).isGreaterThan(0.4);
    }

    @Test
    void theActiveStateIsDrawnWholeOverAStateThatOverlapsIt() {
        // the active state comes first in the model; the plain one after it overlaps its right side
        StateMachine machine = machine(new MachineState("a", "S0", new DiagramPoint(150, 250), List.of()),
                new MachineState("b", "S1", new DiagramPoint(200, 270), List.of()));
        ActiveMachineState active = new ActiveMachineState("a", Optional.empty(), Optional.empty());

        BufferedImage image = paint(machine, Optional.of(active), new MachineSelection(), ColorSchemes.DARK);

        // S0's ring at its right end lies inside S1's circle: drawn last, the active ring stays on top
        Color ring = new Color(image.getRGB((int) Math.round(150 + MachineDiagramGeometry.STATE_RADIUS), 250));
        assertThat(distance(ring, ColorSchemes.DARK.machineActive())).isLessThan(NEAR);
    }

    @Test
    void aStatesHaloIsDrawnWithItOverTheStatesBeforeIt() {
        // S0 (plain, first) and a warning state after it whose halo reaches into S0
        StateMachine machine = machine(new MachineState("a", "S0", new DiagramPoint(150, 250), List.of()),
                new MachineState("lost", "S9", new DiagramPoint(232, 250), List.of()));
        MachineGlows glows = MachineGlows.of(MachineChecks.check(machine));
        assertThat(glows.ofState("lost")).contains(MachineGlows.Glow.WARNING);
        ColorScheme dark = ColorSchemes.DARK;

        BufferedImage image = paint(machine, Optional.empty(), new MachineSelection(), glows, dark);

        // inside S0, in the halo's band of S9 (its circle starts at 192): tinted, not S0's plain inside
        Color inS0 = new Color(image.getRGB(186, 250));
        assertThat(distance(inS0, dark.background())).as("the halo over S0, whole with its state").isGreaterThan(30);
    }

    @Test
    void aWarningStateWearsABadgeWithAnExclamationOnItsRimInBothThemes() {
        // hand test of hito 2, round 2: colour alone made a warning read as the active state
        for (ColorScheme scheme : List.of(ColorSchemes.DARK, ColorSchemes.LIGHT)) {
            StateMachine machine = machine(new MachineState("a", "A", new DiagramPoint(150, 250), List.of()),
                    new MachineState("lost", "S0", new DiagramPoint(550, 250), List.of()));
            MachineGlows glows = MachineGlows.of(MachineChecks.check(machine));
            MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);

            BufferedImage image = paint(machine, Optional.empty(), new MachineSelection(), glows, scheme);

            assertBadge(image, MachineDiagramPainter.stateBadgeCentre(geometry, "lost"), scheme.warning(), scheme);
        }
    }

    @Test
    void anArrowWithAnErrorWearsARedBadgeBesideItsLabel() {
        StateMachine machine = machine(new MachineState("a", "A", new DiagramPoint(150, 250), List.of()),
                new MachineState("b", "B", new DiagramPoint(550, 250), List.of()))
                .withTransitions(List.of(new MachineTransition("t1", "a", "b", "x +", List.of()),
                        new MachineTransition("t2", "b", "a", "", List.of())));
        MachineGlows glows = MachineGlows.of(MachineChecks.check(machine));
        assertThat(glows.ofTransition("t1")).contains(MachineGlows.Glow.ERROR);
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);
        ColorScheme dark = ColorSchemes.DARK;

        BufferedImage image = paint(machine, Optional.empty(), new MachineSelection(), glows, dark);

        Point2D badge = MachineDiagramPainter.arrowBadgeCentre(machine.transition("t1").orElseThrow(),
                geometry.arrow("t1"), metrics(MachineDiagramPainter.labelFont()));
        assertBadge(image, badge, dark.wireConflict(), dark);
    }

    /**
     * The «!» is the cue that does not depend on colours: beside the label of an arrow between states drawn close
     * together it lands on a state, and is still painted over that state's opaque inside (review of the hand fixes,
     * M2).
     */
    @Test
    void anArrowsBadgeOverAStateIsStillSeen() {
        StateMachine machine = machine(new MachineState("a", "A", new DiagramPoint(150, 250), List.of()),
                new MachineState("b", "B", new DiagramPoint(215, 250), List.of()))
                .withTransitions(List.of(new MachineTransition("t1", "a", "b", "x +", List.of())));
        MachineGlows glows = MachineGlows.of(MachineChecks.check(machine));
        MachineDiagramGeometry geometry = MachineDiagramGeometry.of(machine);
        Point2D badge = MachineDiagramPainter.arrowBadgeCentre(machine.transition("t1").orElseThrow(),
                geometry.arrow("t1"), metrics(MachineDiagramPainter.labelFont()));
        assertThat(badge.distance(geometry.centre("b"))).as("the badge is on B")
                .isLessThan(geometry.radius("b") - MachineDiagramPainter.BADGE_RADIUS);
        ColorScheme dark = ColorSchemes.DARK;

        BufferedImage image = paint(machine, Optional.empty(), new MachineSelection(), glows, dark);

        assertBadge(image, badge, dark.wireConflict(), dark);
    }

    /**
     * Around {@code centre}, a disc mostly of {@code colour} (not a thin ring nor a translucent halo) with a mark of
     * another colour down its middle (the «!»).
     */
    private static void assertBadge(BufferedImage image, Point2D centre, Color colour, ColorScheme scheme) {
        int inside = 0;
        int coloured = 0;
        double reach = MachineDiagramPainter.BADGE_RADIUS - 1.5;
        for (int x = (int) (centre.getX() - reach); x <= centre.getX() + reach; x++) {
            for (int y = (int) (centre.getY() - reach); y <= centre.getY() + reach; y++) {
                if (Math.hypot(x - centre.getX(), y - centre.getY()) <= reach) {
                    inside++;
                    coloured += distance(new Color(image.getRGB(x, y)), colour) < NEAR ? 1 : 0;
                }
            }
        }
        assertThat(coloured).as("a solid badge in %s", scheme).isGreaterThan(inside / 2);
        boolean mark = false;
        for (int y = (int) (centre.getY() - reach / 2); y <= centre.getY() + reach / 2; y++) {
            mark |= distance(new Color(image.getRGB((int) Math.round(centre.getX()), y)), colour) > 2 * NEAR;
        }
        assertThat(mark).as("an «!» on it in %s", scheme).isTrue();
    }

    private static void assertInside(Rectangle2D box, Point2D centre, double radius) {
        for (Point2D corner : List.of(new Point2D.Double(box.getMinX(), box.getMinY()),
                new Point2D.Double(box.getMaxX(), box.getMinY()), new Point2D.Double(box.getMinX(), box.getMaxY()),
                new Point2D.Double(box.getMaxX(), box.getMaxY()))) {
            assertThat(corner.distance(centre)).as("corner %s", corner).isLessThan(radius);
        }
    }

    private static FontMetrics metrics(java.awt.Font font) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        try {
            return graphics.getFontMetrics(font);
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage paint(StateMachine machine, Optional<ActiveMachineState> active,
                                       MachineSelection selection, ColorScheme scheme) {
        return paint(machine, active, selection, MachineGlows.NONE, scheme);
    }

    private static BufferedImage paint(StateMachine machine, Optional<ActiveMachineState> active,
                                       MachineSelection selection, MachineGlows glows, ColorScheme scheme) {
        return paint(machine, active, selection, glows, scheme, 1);
    }

    private static BufferedImage paint(StateMachine machine, Optional<ActiveMachineState> active,
                                       MachineSelection selection, MachineGlows glows, ColorScheme scheme,
                                       double zoom) {
        BufferedImage image = new BufferedImage((int) (WIDTH * zoom), (int) (HEIGHT * zoom),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(scheme.background());
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.scale(zoom, zoom);
        MachineDiagramPainter.paint(graphics, machine, MachineDiagramGeometry.of(machine),
                new MachineDiagramPainter.Marks(active, selection, glows), scheme);
        graphics.dispose();
        return image;
    }

    private static double distance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }
}
