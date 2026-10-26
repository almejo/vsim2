package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.command.ReplaceDefinitionCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ConnectivityProblemsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private OpenDocument openDocument;
    private EditorView view;
    private ComponentDefinition broken;
    private Component outer;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(registry);
        Component lamp = registry.require(StandardComponentTypes.LED).newComponent(new ElementId("lamp"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "Luz");
        broken = new ComponentDefinition("child", "Hijo", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(lamp), List.of(), List.of()));
        openDocument.document().putDefinition(broken);
        Component child = openDocument.registry().require(SubcircuitTypes.typeIdOf("child"))
                .newComponent(new ElementId("child-instance"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "A1");
        openDocument.document().putDefinition(new ComponentDefinition("parent", "Padre", "", new GridSize(4, 4),
                List.of(), new CircuitFragment(List.of(child), List.of(), List.of())));
        outer = place(openDocument, SubcircuitTypes.typeIdOf("parent"), 10, 10, Map.of());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    @Test
    void placingAnUnwiredHealthyComponentDoesNotAddProblemsToThePanel() {
        List<ConnectivityProblem> before = openDocument.connectivityProblems();
        openDocument.document().putDefinition(ExampleDefinitions.inverter());
        place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0, Map.of());

        assertThat(openDocument.connectivityProblems()).containsExactlyElementsOf(before);
        assertThat(view.problems().navigator().problems()).containsExactlyElementsOf(before);
    }

    @Test
    void internalWarningIsAvailableOnOpeningWithoutWaitingForSimulation() {
        assertThat(view.problems().navigator().problems()).singleElement().isInstanceOf(ConnectivityProblem.class);
        assertThat(view.problems().nextProblem().isEnabled()).isTrue();
        ConnectivityProblem problem = openDocument.connectivityProblems().getFirst();
        assertThat(ProblemText.describe(problem, openDocument.document(), openDocument.registry()))
                .contains("Padre", "A1", "Luz").contains("la entrada «").contains("» está desconectada");
    }

    @Test
    void navigatingToAnInternalPinSelectsItsOuterVisibleComponent() {
        ConnectivityProblem problem = openDocument.connectivityProblems().getFirst();

        assertThat(view.problems().navigator().navigateTo(problem)).isTrue();

        assertThat(view.canvas().selection().ids()).containsExactly(outer.id());
    }

    @Test
    void repairingANestedDefinitionClearsWarningsImmediatelyAndUndoRestoresThem() {
        repairChild();

        assertThat(openDocument.connectivityProblems()).isEmpty();
        assertThat(view.problems().navigator().problems()).isEmpty();
        openDocument.history().undo();
        assertThat(view.problems().navigator().problems()).singleElement().isInstanceOf(ConnectivityProblem.class);
        openDocument.history().redo();
        assertThat(view.problems().navigator().problems()).isEmpty();
    }

    @Test
    void theOuterWarningUsesTheCurrentThemeAndDisappearsAfterRepair() {
        AtomicReference<ColorScheme> scheme = new AtomicReference<>(ColorScheme.LIGHT);
        ConnectivityOverlay overlay = new ConnectivityOverlay(openDocument, scheme::get);
        int light = warningPixel(overlay);
        assertThat(light).isNotZero();
        scheme.set(ColorSchemes.DARK);
        assertThat(warningPixel(overlay)).isNotZero().isNotEqualTo(light);

        repairChild();

        assertThat(warningPixel(overlay)).isZero();
    }

    @Test
    void aComponentBrokenInsideGlowsAsAWholeInTheCurrentThemeAndStopsGlowingAfterRepair() {
        AtomicReference<ColorScheme> scheme = new AtomicReference<>(ColorScheme.LIGHT);
        ConnectivityOverlay overlay = new ConnectivityOverlay(openDocument, scheme::get);
        int light = bodyPixel(overlay);
        assertThat(light).isNotZero();
        scheme.set(ColorSchemes.DARK);
        assertThat(bodyPixel(overlay)).isNotZero().isNotEqualTo(light);

        repairChild();

        assertThat(bodyPixel(overlay)).isZero();
    }

    @Test
    void theProblemsLineNamesTheFullPathAndTheKindOfPin() {
        ConnectivityProblem problem = openDocument.connectivityProblems().getFirst();

        assertThat(ProblemText.describe(problem, openDocument.document(), openDocument.registry()))
                .startsWith("Padre › A1 › Luz")
                .contains("la entrada «")
                .endsWith("» está desconectada");
        assertThat(problem.path()).hasSize(3).first().isEqualTo(outer.id());
    }

    private int bodyPixel(ConnectivityOverlay overlay) {
        BufferedImage image = new BufferedImage(180, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.paint(graphics, new RenderState(openDocument.document(), openDocument.netlist(),
                openDocument.netlistToken(), SimulationSnapshot.EMPTY, Set.of(), 0));
        graphics.dispose();
        // The centre of the outer piece (10,10 .. 14,14), away from the triangle at its top-right corner.
        return image.getRGB(12 * GridPoint.PIXELS_PER_UNIT, 12 * GridPoint.PIXELS_PER_UNIT);
    }

    private int warningPixel(ConnectivityOverlay overlay) {
        BufferedImage image = new BufferedImage(180, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.paint(graphics, new RenderState(openDocument.document(), openDocument.netlist(),
                openDocument.netlistToken(), SimulationSnapshot.EMPTY, Set.of(), 0));
        graphics.dispose();
        // The triangle sits at the outer instance's top-right, not at the internal pin's local coordinates.
        return image.getRGB(114, 87);
    }

    private void repairChild() {
        Component lamp = broken.body().components().getFirst();
        Component source = registry.require(StandardComponentTypes.SWITCH).newComponent(new ElementId("source"),
                new GridPoint(-3, 2), Rotation.R0, Map.of(), "");
        ComponentDefinition repaired = new ComponentDefinition(broken.id(), broken.name(), broken.description(),
                broken.size(), broken.pinout(), new CircuitFragment(List.of(lamp, source), List.of(), List.of()));

        openDocument.execute(ReplaceDefinitionCommand.prepare(openDocument.document(), openDocument.registry(),
                new DefinitionBundle(repaired, List.of())));
    }
}
