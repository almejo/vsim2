package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.SelectTool;
import cl.almejo.vsim.ui.tools.TestToolContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review focus 2 (spec §10.4): a component tab whose working copy follows a change made elsewhere (an undo from the
 * circuit's tab) cancels the gesture in progress as Escape would, and its selection keeps the ids that still exist.
 */
class CircuitCanvasRefreshTest {

    /** The inverter's NOT gate, at (2, 0). */
    private static final ElementId GATE = new ElementId("c2");
    private static final ElementId ADDED = new ElementId("c9");

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private OpenDocument body;
    private CircuitCanvas canvas;

    @BeforeEach
    void enterAnInverter() {
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c5"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""))));
        body = circuit.openBody(ExampleDefinitions.INVERTER, Optional.of("c5"));
        canvas = new CircuitCanvas(body, new CircuitRenderer(body.registry(), PainterRegistry.standard(),
                ColorScheme.LIGHT), new CanvasHost() {
            @Override
            public void showContextMenu(CircuitCanvas source, Optional<DocumentElement> target,
                                        Point2D screenPosition) {
            }

            @Override
            public void configure(Component component) {
            }
        });
        canvas.setSize(400, 300);
    }

    @AfterEach
    void closeAll() {
        canvas.dispose();
        body.close();
        circuit.close();
    }

    @Test
    void aRefreshCancelsTheGestureAndKeepsValidSelection() {
        body.execute(new AddElementsCommand(List.of(StandardComponentTypes.registry()
                .require(StandardComponentTypes.BUFFER).newComponent(ADDED, new GridPoint(20, 20), Rotation.R0,
                        Map.of(), ""))));
        canvas.selection().set(List.of(GATE, ADDED));
        SelectTool tool = (SelectTool) canvas.tool();
        tool.mousePressed(canvas, TestToolContext.at(3, 1));
        tool.mouseDragged(canvas, TestToolContext.at(6, 1));
        assertThat(tool.isIdle()).isFalse();

        circuit.history().undo(); // from the circuit's tab: the gate added inside goes

        assertThat(tool.isIdle()).isTrue();
        assertThat(canvas.selection().ids()).containsExactly(GATE);
        tool.mouseReleased(canvas, TestToolContext.at(6, 1));
        assertThat(body.document().component(GATE).orElseThrow().position()).isEqualTo(new GridPoint(2, 0));
    }

    @Test
    void anIdleRefreshKeepsTheWholeSelection() {
        body.execute(new AddElementsCommand(List.of(StandardComponentTypes.registry()
                .require(StandardComponentTypes.BUFFER).newComponent(ADDED, new GridPoint(20, 20), Rotation.R0,
                        Map.of(), ""))));
        circuit.history().undo();
        canvas.selection().set(List.of(GATE));

        circuit.history().redo();

        assertThat(canvas.selection().ids()).containsExactly(GATE);
    }

    @Test
    void disposingTheCanvasRemovesItsRefreshListener() {
        int before = body.refreshListenerCount();
        canvas.dispose();
        assertThat(body.refreshListenerCount()).isEqualTo(before - 1);
    }
}
