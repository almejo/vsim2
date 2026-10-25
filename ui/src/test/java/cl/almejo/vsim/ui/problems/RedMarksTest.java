package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.OutdatedPieceChecks;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.LoosenedPinsTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The marks of an outer terminal whose interface element is unconnected, of loosened pins and of old versions. */
class RedMarksTest {

    private static final int UNIT = GridPoint.PIXELS_PER_UNIT;

    private OpenDocument openDocument;
    private EditorView view;
    private Component piece;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        Component input = openDocument.registry().require(InterfacePins.INPUT_PIN).newComponent(
                new ElementId("interface"), GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        openDocument.document().putDefinition(new ComponentDefinition("interface-only", "Interfaz", "",
                new GridSize(4, 4), List.of(new DefinitionPin(PinSpec.input("A", 0, 1), input.id())),
                new CircuitFragment(List.of(input), List.of(), List.of())));
        piece = openDocument.registry().require(SubcircuitTypes.typeIdOf("interface-only")).newComponent(
                new ElementId("piece"), new GridPoint(10, 10), Rotation.R0, Map.of(), "U5");
        openDocument.execute(new AddElementsCommand(List.of(piece)));
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }
        });
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    @Test
    void anOuterTerminalWhoseInterfaceElementIsUnconnectedGlowsOnTheTerminalOnly() {
        AtomicReference<ColorScheme> scheme = new AtomicReference<>(ColorScheme.LIGHT);
        ConnectivityOverlay overlay = new ConnectivityOverlay(openDocument, scheme::get);
        GridPoint pin = openDocument.registry().typeOf(piece).pinLocation(piece, 0);

        int light = pixel(overlay, pin.x() * UNIT, pin.y() * UNIT);
        scheme.set(ColorSchemes.DARK);

        assertThat(light).isNotZero();
        assertThat(pixel(overlay, pin.x() * UNIT, pin.y() * UNIT)).isNotZero().isNotEqualTo(light);
        assertThat(pixel(overlay, 12 * UNIT, 12 * UNIT)).as("the piece does not glow as a whole").isZero();
    }

    @Test
    void theWarningTriangleOfAPinSitsOutsideThePieceWhereItCoversNoPinName() {
        ConnectivityOverlay overlay = new ConnectivityOverlay(openDocument, () -> ColorScheme.LIGHT);
        GridPoint pin = openDocument.registry().typeOf(piece).pinLocation(piece, 0);
        int pinX = pin.x() * UNIT;
        int pinY = pin.y() * UNIT;

        assertThat(pixel(overlay, pinX - 9, pinY - 4)).as("the triangle on the stub side").isNotZero();
        assertThat(pixel(overlay, pinX + 9, pinY - 4)).as("nothing inside the body next to the pin").isZero();
    }

    @Test
    void theProblemsLineNamesThePieceAndTheTerminalAndNavigationSelectsThePiece() {
        ConnectivityProblem problem = openDocument.connectivityProblems().getFirst();

        assertThat(problem.cause()).isEqualTo(ConnectivityProblem.Cause.UNCONNECTED_INTERFACE);
        assertThat(ProblemText.describe(problem, openDocument.document(), openDocument.registry()))
                .contains("U5", "terminal «A»", "por dentro");
        assertThat(view.problems().navigator().navigateTo(problem)).isTrue();
        assertThat(view.canvas().selection().ids()).containsExactly(piece.id());
    }

    @Test
    void aPinLoosenedByAShapeChangeGlowsAndIsListedAfterTheNextRefresh() {
        ConnectivityOverlay overlay = new ConnectivityOverlay(openDocument, () -> ColorScheme.LIGHT);
        GridPoint pin = openDocument.registry().typeOf(piece).pinLocation(piece, 0);

        LoosenedPinsTestSupport.set(openDocument, Set.of(new PinRef(piece.id(), 0)));
        openDocument.execute(new AddElementsCommand(List.of(openDocument.registry().require(StandardComponentTypes.LED)
                .newComponent(new ElementId("other"), new GridPoint(40, 40), Rotation.R0, Map.of(), ""))));

        assertThat(pixel(overlay, pin.x() * UNIT, pin.y() * UNIT)).isNotZero();
        ConnectivityProblem loosened = view.problems().navigator().problems().stream()
                .filter(ConnectivityProblem.class::isInstance).map(ConnectivityProblem.class::cast)
                .filter(problem -> problem.cause() == ConnectivityProblem.Cause.LOOSENED_BY_SHAPE_CHANGE)
                .findFirst().orElseThrow();
        assertThat(ProblemText.describe(loosened, openDocument.document(), openDocument.registry()))
                .isEqualTo("Pin desconectado al cambiar la forma: U5 · A");
    }

    @Test
    void aPieceWhoseUpdateWouldBreakItIsListedWithTheOlderDefinitionsName() {
        ConnectivityProblem outdated = new ConnectivityProblem(OutdatedPieceChecks.VERSION_PREFIX + "interface-only",
                piece.id().value(), -1, piece.position(), ConnectivityProblem.Cause.OUTDATED_WOULD_BREAK);

        view.problems().showOutdatedPieces(List.of(outdated));

        assertThat(view.problems().navigator().problems()).contains(outdated);
        assertThat(ProblemText.describe(outdated, openDocument.document(), openDocument.registry()))
                .isEqualTo("U5 usa una versión anterior de Interfaz; actualizarla dejaría conexiones internas"
                        + " sin conectar.");
        assertThat(view.problems().navigator().navigateTo(outdated)).isTrue();
        assertThat(view.canvas().selection().ids()).containsExactly(piece.id());
    }

    private int pixel(ConnectivityOverlay overlay, int x, int y) {
        BufferedImage image = new BufferedImage(260, 260, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        overlay.paint(graphics, new RenderState(openDocument.document(), openDocument.netlist(),
                openDocument.netlistToken(), SimulationSnapshot.EMPTY, Set.of(), 0));
        graphics.dispose();
        return image.getRGB(x, y);
    }
}
