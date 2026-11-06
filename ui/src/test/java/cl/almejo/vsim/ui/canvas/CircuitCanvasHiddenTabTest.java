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
 * A canvas whose tab is not the one shown (the tabs are cards that all stay added) does not run its frame timer: it
 * would remap the whole snapshot and call its listeners every frame for nothing; it catches up when it is shown.
 */
class CircuitCanvasHiddenTabTest {

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
    void aHiddenCanvasStopsItsFrameTimerAndResumesWhenShown() {
        canvas.addNotify();
        assertThat(canvas.framesRunning()).isTrue();

        canvas.showingChanged(false);
        assertThat(canvas.framesRunning()).isFalse();

        canvas.showingChanged(true);
        assertThat(canvas.framesRunning()).isTrue();
    }

    @Test
    void aCanvasRemovedWhileHiddenIsNotRestartedByAShowingEvent() {
        canvas.addNotify();
        canvas.removeNotify();

        canvas.showingChanged(true);

        assertThat(canvas.framesRunning()).isFalse();
    }
}
