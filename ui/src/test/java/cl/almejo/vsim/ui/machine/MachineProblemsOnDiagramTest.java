package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineLimits;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.pinAt;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A machine's problems on its diagram (plan 2026-11-14, hito 2 task 8): red and amber glows, the words when the pointer
 * rests on them, the problems in «Propiedades», and F8 / Mayús+F8 walking the machine's problems in its tab. The copy
 * runs with its CLK unconnected, so besides the model's three problems it reports a fourth of its own.
 */
class MachineProblemsOnDiagramTest {

    /** How far from a state's centre its halo is sampled: past the outline, inside the halo. */
    /** How far under a state's centre the halo is looked for: past its circle, before anything else. */
    private static final double HALO_REACH = MachineDiagramGeometry.STATE_RADIUS + 12;

    @TempDir
    Path directory;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<EditorView> views = new ArrayList<>();
    private LibraryService service;
    private OpenDocument document;
    private OpenDocument place;
    private EditorView view;
    private MachineMode mode;
    private MachineCanvas canvas;

    /**
     * From A, «x» to B and «x + y» to C hold at once with x = 1, y = 0 (an error); D is never reached and A stays with
     * x = 0, y = 0 (two warnings).
     */
    static StateMachine troubled() {
        List<MachineState> states = List.of(
                new MachineState("a", "A", new DiagramPoint(0, 0), List.of()),
                new MachineState("b", "B", new DiagramPoint(200, -120), List.of()),
                new MachineState("c", "C", new DiagramPoint(200, 120), List.of()),
                new MachineState("d", "D", new DiagramPoint(420, 0), List.of()));
        List<MachineTransition> transitions = List.of(
                new MachineTransition("t1", "a", "b", "x", List.of()),
                new MachineTransition("t2", "a", "c", "x + y", List.of()));
        return new StateMachine(states, transitions, Optional.of("a"), List.of("x", "y"), List.of(), false, false,
                StateMachine.EDGE_FALLING, List.of());
    }

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        ComponentLibrary library = new ComponentLibrary();
        service = new LibraryService(preferences, library, registry, Runnable::run);
        List<EditorExtension> extensions = new ArrayList<>(
                Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE).stream()
                        .filter(extension -> !(extension instanceof MachineModeExtension))
                        .toList());
        extensions.add(new MachineModeExtension((snapshot, instancePath) -> Optional.empty()));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, extensions);
        document = OpenDocument.createEmpty(registry);
        document.execute(new PutDefinitionsCommand(List.of(
                MachineDefinitions.regenerated(MachineFixtures.definition(troubled())))));
        Component piece = place(document, SubcircuitTypes.typeIdOf(MachineFixtures.DEFINITION_ID), 0, 0, Map.of());
        String copy = piece.id().value();
        // CLK driven by an inverter whose input is free: neither 0 nor 1 (a free CLK is not told)
        int clockIndex = document.document().definition(MachineFixtures.DEFINITION_ID).orElseThrow()
                .pinIndexOf(MachineLimits.CLOCK).orElseThrow();
        GridPoint clockPin = document.registry().typeOf(piece).pinLocation(piece, clockIndex);
        pinAt(document, clockPin.x() - 4, clockPin.y());
        addWire(document, clockPin.x() - 4, clockPin.y(), clockPin.x(), clockPin.y());
        awaitSimulation(document); // the copy's own problem (CLK without 0 or 1) is listed from the first refresh
        place = document.openBody(MachineFixtures.DEFINITION_ID, Optional.of(copy));
        view = new EditorView(place, services, NO_WINDOW);
        view.setSize(1200, 800);
        view.doLayout();
        views.add(view);
        mode = MachineMode.of(view).orElseThrow();
        canvas = mode.canvas();
        canvas.setBounds(0, 0, 900, 600);
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        place.close();
        document.close();
        service.close();
    }

    private Point2D at(Point2D diagramPoint) {
        return canvas.toScreen(new DiagramPoint(diagramPoint.getX(), diagramPoint.getY()));
    }

    private Optional<String> toolTipAt(Point2D diagramPoint) {
        Point2D screen = at(diagramPoint);
        MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, (int) Math.round(screen.getX()),
                (int) Math.round(screen.getY()), 0, false);
        return Optional.ofNullable(canvas.getToolTipText(event));
    }

    private MachineDiagramGeometry geometry() {
        return MachineDiagramGeometry.of(canvas.machine().orElseThrow());
    }

    private MachineProblem problemOf(MachineFinding.Kind kind) {
        return canvas.problems().stream().filter(problem -> problem.finding().kind() == kind).findFirst()
                .orElseThrow();
    }

    @Test
    void twoArrowsTrueAtOnceGlowRedAndTheirTooltipShowsTheWitness() {
        MachineGlows glows = canvas.glows();

        assertThat(glows.ofTransition("t1")).contains(MachineGlows.Glow.ERROR);
        assertThat(glows.ofTransition("t2")).contains(MachineGlows.Glow.ERROR);
        assertThat(glows.ofState("a")).as("A is amber for staying with x = 0, y = 0, not red: its arrows are")
                .contains(MachineGlows.Glow.WARNING);
        assertThat(glows.ofState("b")).isEmpty();
        assertThat(toolTipAt(geometry().arrow("t1").labelAnchor())).hasValueSatisfying(text -> assertThat(text)
                .contains("se cumplen a la vez").contains("x = 1, y = 0"));
    }

    @Test
    void anUnreachableStateGlowsAmberAndSaysWhy() {
        assertThat(canvas.glows().ofState("d")).contains(MachineGlows.Glow.WARNING);
        assertThat(toolTipAt(geometry().centre("d"))).hasValueSatisfying(text -> assertThat(text)
                .contains("D").contains("no se alcanza"));
        assertThat(toolTipAt(geometry().centre("b"))).isEmpty();
    }

    @Test
    void theHaloColourIsReadFromTheSchemeAtEachPaint() {
        AtomicReference<ColorScheme> scheme = new AtomicReference<>(ColorScheme.LIGHT);
        MachineCanvas standalone = new MachineCanvas(place, MachineFixtures.DEFINITION_ID, new MachineSelection(),
                (snapshot, instancePath) -> Optional.empty(), scheme::get);
        standalone.setBounds(0, 0, 900, 600);
        Point2D centre = geometry().centre("d");

        Color light = haloPixel(standalone, centre);
        scheme.set(ColorSchemes.DARK);
        Color dark = haloPixel(standalone, centre);
        standalone.dispose();

        assertThat(light).isNotEqualTo(ColorScheme.LIGHT.background());
        assertThat(dark).isNotEqualTo(ColorSchemes.DARK.background()).isNotEqualTo(light);
        assertThat(closerTo(light, ColorScheme.LIGHT.warning(), ColorScheme.LIGHT.wireConflict()))
                .as("amber, not red").isTrue();
        assertThat(closerTo(dark, ColorSchemes.DARK.warning(), ColorSchemes.DARK.wireConflict())).isTrue();
    }

    @Test
    void afterTheFixTheGlowIsGoneWithoutASimulationTick() {
        canvas.edit(machine -> MachineEdits.withCondition(machine, "t2", "x' y"));

        assertThat(canvas.glows().ofTransition("t1")).isEmpty();
        assertThat(canvas.glows().ofTransition("t2")).isEmpty();
        assertThat(toolTipAt(geometry().arrow("t1").labelAnchor())).isEmpty();
    }

    @Test
    void revealingAProblemSelectsWhatGlowsForIt() {
        mode.reveal(problemOf(MachineFinding.Kind.NONDETERMINISTIC));

        assertThat(mode.selection().transitionIds()).containsExactlyInAnyOrder("t1", "t2");
        assertThat(mode.selection().stateIds()).isEmpty();

        mode.reveal(problemOf(MachineFinding.Kind.UNREACHABLE_STATE));

        assertThat(mode.selection().stateIds()).containsExactly("d");
        assertThat(mode.selection().transitionIds()).isEmpty();
    }

    @Test
    void f8WalksTheMachinesProblemsOnTheDiagramAndCycles() {
        List<Set<String>> selected = new ArrayList<>();
        for (int step = 0; step < 5; step++) {
            perform(view.problems().nextProblem());
            selected.add(selectedIds());
        }

        // the copy's CLK problem is about no state or arrow: it selects nothing (Propiedades then list it)
        assertThat(selected.subList(0, 4)).containsExactlyInAnyOrder(Set.of("t1", "t2"), Set.of("d"), Set.of("a"),
                Set.of());
        assertThat(selected.get(0)).as("errors first").isEqualTo(Set.of("t1", "t2"));
        assertThat(selected.get(4)).as("it goes round").isEqualTo(selected.get(0));
        perform(view.problems().previousProblem());
        assertThat(selectedIds()).isEqualTo(selected.get(3));
    }

    private Set<String> selectedIds() {
        Set<String> ids = new HashSet<>(mode.selection().stateIds());
        ids.addAll(mode.selection().transitionIds());
        return ids;
    }

    @Test
    void f8AndMayusF8AreTheDiagramsKeysToo() {
        KeyStroke next = KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0);
        KeyStroke previous = KeyStroke.getKeyStroke(KeyEvent.VK_F8, java.awt.event.InputEvent.SHIFT_DOWN_MASK);

        Object nextName = canvas.getInputMap(JComponent.WHEN_FOCUSED).get(next);
        Object previousName = canvas.getInputMap(JComponent.WHEN_FOCUSED).get(previous);

        assertThat(canvas.getActionMap().get(nextName)).isSameAs(view.problems().nextProblem());
        assertThat(canvas.getActionMap().get(previousName)).isSameAs(view.problems().previousProblem());
    }

    @Test
    void theSelectedArrowsPropertiesSayWhatIsWrongWithIt() {
        mode.selection().selectTransition("t1");

        assertThat(mode.inspectorRows().problemLines()).singleElement().satisfies(text -> assertThat(text)
                .contains("x = 1, y = 0"));

        mode.selection().selectState("b");

        assertThat(mode.inspectorRows().problemLines()).isEmpty();
    }

    @Test
    void withNothingSelectedThePropertiesListTheMachinesProblemsAndEachOneReveals() {
        mode.selection().clear();

        List<String> lines = mode.inspectorRows().problemLines();
        List<AbstractButton> links = mode.inspectorRows().problemLinks();
        assertThat(lines).hasSize(4);
        assertThat(lines.getFirst()).as("an error says so in words").startsWith("Error: ");
        assertThat(lines).anySatisfy(line -> assertThat(line).startsWith("Aviso: ").contains("no se alcanza"));
        assertThat(lines.getLast()).as("the running copy's own").contains("CLK no tiene 0 ni 1");
        assertThat(links).hasSize(4);
        int unreachable = lines.indexOf(lines.stream().filter(line -> line.contains("no se alcanza")).findFirst()
                .orElseThrow());

        links.get(unreachable).doClick();

        assertThat(mode.selection().stateIds()).containsExactly("d");
    }

    /**
     * The most saturated pixel straight under the state at {@code centre}, just past its circle (up to
     * {@link #HALO_REACH}): the halo, wherever its width and antialiasing put it.
     */
    private static Color haloPixel(MachineCanvas painted, Point2D centre) {
        BufferedImage image = new BufferedImage(painted.getWidth(), painted.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        painted.paint(graphics);
        graphics.dispose();
        Color best = null;
        float bestSaturation = -1;
        for (double distance = MachineDiagramGeometry.STATE_RADIUS + 2; distance <= HALO_REACH; distance += 0.5) {
            Point2D point = painted.toScreen(new DiagramPoint(centre.getX(), centre.getY() + distance));
            Color colour = new Color(image.getRGB((int) Math.round(point.getX()), (int) Math.round(point.getY())));
            float saturation = Color.RGBtoHSB(colour.getRed(), colour.getGreen(), colour.getBlue(), null)[1];
            if (saturation > bestSaturation) {
                best = colour;
                bestSaturation = saturation;
            }
        }
        return best;
    }

    /** Whether {@code colour} is nearer {@code expected} than {@code other} in hue (red to green ratio). */
    private static boolean closerTo(Color colour, Color expected, Color other) {
        return distance(colour, expected) < distance(colour, other);
    }

    private static double distance(Color first, Color second) {
        float[] firstHsb = Color.RGBtoHSB(first.getRed(), first.getGreen(), first.getBlue(), null);
        float[] secondHsb = Color.RGBtoHSB(second.getRed(), second.getGreen(), second.getBlue(), null);
        double hue = Math.abs(firstHsb[0] - secondHsb[0]);
        return Math.min(hue, 1 - hue);
    }
}
