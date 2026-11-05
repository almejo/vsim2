package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.InterfacePinPainter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.awaitSimulation;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inside a component its interface pins take their value from outside (spec §6.3): a click does not toggle them, a
 * double click edits no value, and the status line says why.
 */
class InterfacePinsInsideTest {

    private static final String HINT = "Su valor viene de afuera";
    /** The 8-bit input pin «a» of the byte buffer's body, at (0, 1). */
    private static final ElementId BUS_INPUT = new ElementId("c1");

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final SelectTool tool = new SelectTool();
    private OpenDocument body;
    private TestToolContext context;

    @BeforeEach
    void enterAByteBuffer() {
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.byteBuffer())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c5"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""))));
        body = circuit.openBody(ExampleDefinitions.BYTE_BUFFER, Optional.of("c5"));
        context = new TestToolContext(body);
    }

    @AfterEach
    void closeAll() {
        body.close();
        circuit.close();
    }

    @Test
    void clickOnAnInterfacePinInsideDoesNothing() {
        awaitSimulation(circuit);
        VisibleState before = body.latestSnapshot().componentState(BUS_INPUT.value());
        Optional<Command> lastStep = circuit.history().undoCommand();

        context.click(tool, 0.6, 2);
        awaitSimulation(circuit);

        assertThat(body.latestSnapshot().componentState(BUS_INPUT.value())).isEqualTo(before);
        assertThat(circuit.history().undoCommand()).isEqualTo(lastStep);
        assertThat(context.notices).containsExactly(HINT);
    }

    @Test
    void doubleClickOnTheValueBoxOfAnInterfacePinInsideEditsNoValue() {
        Component pin = body.document().component(BUS_INPUT).orElseThrow();
        Rectangle2D box = InterfacePinPainter.valueBox(body.registry().typeOf(pin), pin).orElseThrow();
        double gridX = box.getCenterX() / GridPoint.PIXELS_PER_UNIT;
        double gridY = box.getCenterY() / GridPoint.PIXELS_PER_UNIT;

        tool.mousePressed(context, TestToolContext.at(gridX, gridY));
        tool.mouseReleased(context, TestToolContext.at(gridX, gridY));
        tool.mousePressed(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, 2, false));
        tool.mouseReleased(context, TestToolContext.event(gridX, gridY, CanvasMouseEvent.PRIMARY, 2, false));

        assertThat(context.valueEdited).isNull();
        assertThat(context.notices).contains(HINT);
    }
}
