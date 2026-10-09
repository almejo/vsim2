package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CanvasHost;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.CircuitRenderer;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ProblemNavigatorTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final CircuitCanvas canvas = new CircuitCanvas(openDocument,
            new CircuitRenderer(openDocument.registry(), PainterRegistry.standard(), ColorScheme.LIGHT),
            new CanvasHost() {
                @Override
                public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target,
                                            Point2D screenPosition) {
                }

                @Override
                public void configure(Component component) {
                }
            });
    private final NetBlink blink = new NetBlink(ColorScheme.LIGHT, canvas::repaint);
    private final ProblemNavigator navigator = new ProblemNavigator(canvas, blink);
    private Wire left;
    private Wire right;

    @BeforeEach
    void build() {
        canvas.setSize(400, 300);
        left = shortedPair(0);
        right = shortedPair(40);
    }

    @AfterEach
    void close() {
        blink.dispose();
        canvas.dispose();
        openDocument.close();
    }

    private Wire shortedPair(int x) {
        place(openDocument, StandardComponentTypes.SWITCH, x, 0, Map.of("value", "1"));
        place(openDocument, StandardComponentTypes.SWITCH, x, 6, Map.of("value", "0"));
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(x + 4, 1), new GridPoint(x + 4, 7));
        openDocument.execute(new AddWireCommand(wire));
        return wire;
    }

    private String netOf(Wire wire) {
        return openDocument.netlist().netForWire(wire.id()).orElseThrow().id().value();
    }

    private static ShortCircuit shortOn(String netId, long since) {
        return new ShortCircuit(netId, List.of(), since);
    }

    private void show(Problem... problems) {
        navigator.showSnapshot(new SimulationSnapshot(1, openDocument.netlistToken(), 10, true, Map.of(), Map.of(),
                List.of(problems), Optional.empty()));
    }

    @Test
    void nextGoesOldestFirstAndWrapsAround() {
        show(shortOn(netOf(left), 5), shortOn(netOf(right), 2));
        assertThat(navigator.next()).map(Problem::netId).contains(netOf(right));
        assertThat(navigator.next()).map(Problem::netId).contains(netOf(left));
        assertThat(navigator.next()).map(Problem::netId).contains(netOf(right));
        assertThat(navigator.previous()).map(Problem::netId).contains(netOf(left));
    }

    @Test
    void previousFromTheStartGoesToTheLast() {
        show(shortOn(netOf(left), 5), shortOn(netOf(right), 2));
        assertThat(navigator.previous()).map(Problem::netId).contains(netOf(left));
    }

    @Test
    void navigatingCentersSelectsTheWiresAndBlinks() {
        show(shortOn(netOf(right), 2));
        navigator.next();
        assertThat(canvas.selection().ids()).containsExactly(right.id());
        assertThat(canvas.viewport().gridToScreen(44, 4)).isEqualTo(new Point2D.Double(200, 150));
        assertThat(blink.isBlinking()).isTrue();
    }

    @Test
    void aNetWithoutWiresSelectsItsComponents() {
        Component first = place(openDocument, StandardComponentTypes.SWITCH, 0, 20, Map.of("value", "1"));
        ComponentType switchType = openDocument.registry().require(StandardComponentTypes.SWITCH);
        Component second = switchType.newComponent(openDocument.document().newComponentId(), new GridPoint(8, 22),
                Rotation.R180, Map.of("value", "0"), "");
        openDocument.execute(new AddElementsCommand(List.of(second)));
        String netId = openDocument.netlist().netForPin(new PinRef(first.id(), 0)).orElseThrow().id().value();
        assertThat(openDocument.netlist().netForPin(new PinRef(second.id(), 0)).orElseThrow().id().value())
                .isEqualTo(netId);

        show(shortOn(netId, 1));
        assertThat(navigator.next()).isPresent();
        assertThat(canvas.selection().ids()).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(canvas.viewport().gridToScreen(4, 21)).isEqualTo(new Point2D.Double(200, 150));
    }

    @Test
    void aProblemWhoseNetIsGoneChangesNothing() {
        canvas.selection().select(left.id());
        assertThat(navigator.navigateTo(shortOn("n:missing", 1))).isFalse();
        assertThat(canvas.selection().ids()).containsExactly(left.id());
        assertThat(blink.isBlinking()).isFalse();
    }

    @Test
    void withoutProblemsNextAndPreviousDoNothing() {
        show();
        assertThat(navigator.next()).isEmpty();
        assertThat(navigator.previous()).isEmpty();
        assertThat(canvas.selection().isEmpty()).isTrue();
    }

    @Test
    void theCursorStaysOnTheSameNetWhenTheListChanges() {
        show(shortOn(netOf(left), 5), shortOn(netOf(right), 7));
        assertThat(navigator.next()).map(Problem::netId).contains(netOf(left));
        show(shortOn(netOf(right), 7), shortOn(netOf(left), 5), shortOn("n:early", 1));
        assertThat(navigator.problems()).map(Problem::netId).containsExactly("n:early", netOf(left), netOf(right));
        assertThat(navigator.next()).map(Problem::netId).contains(netOf(right));
    }
}
