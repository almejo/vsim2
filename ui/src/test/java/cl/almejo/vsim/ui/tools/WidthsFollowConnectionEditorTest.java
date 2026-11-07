package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.addWire;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Anchos que siguen lo conectado» in the editor: drawing a wire, placing from the palette and moving the selection
 * make the parts they connect take the width they meet, in the connection's undo step, and the status line says so.
 */
class WidthsFollowConnectionEditorTest {

    private static final String WIDTH = StandardComponentTypes.WIDTH;

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final OpenDocument openDocument = context.openDocument();

    @AfterEach
    void close() {
        context.close();
    }

    private int widthOf(OpenDocument target, ElementId id) {
        Component component = target.document().component(id).orElseThrow();
        return (Integer) component.parameters().get(WIDTH);
    }

    private GridPoint pin(OpenDocument target, ElementId id, String name) {
        Component component = target.document().component(id).orElseThrow();
        ComponentType type = target.registry().typeOf(component);
        return type.pinLocation(component, type.pinIndexOf(name).orElseThrow());
    }

    private void drawWire(GridPoint start, GridPoint end) {
        WireDrawing drawing = new WireDrawing(start, new Point2D.Double(start.x(), start.y()));
        drawing.dragTo(context, TestToolContext.at(end.x(), end.y()));
        assertThat(drawing.commit(context, end)).isTrue();
    }

    @Test
    void aWireDrawnFromAOneBitConstantToAnEightBitAdderMakesTheConstantEightBitsInOneUndoStep() {
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 0, 2, Map.of(WIDTH, 1));
        Component adder = place(openDocument, StandardComponentTypes.ADDER, 20, 0, Map.of(WIDTH, 8));

        drawWire(pin(openDocument, constant.id(), "out"), pin(openDocument, adder.id(), "A"));

        assertThat(widthOf(openDocument, constant.id())).isEqualTo(8);
        assertThat(context.notices).containsExactly("Constante tomó el ancho de lo conectado: 8 bits");

        openDocument.history().undo();

        assertThat(widthOf(openDocument, constant.id())).isEqualTo(1);
        assertThat(openDocument.document().wires()).isEmpty();
    }

    @Test
    void anOutputPinPlacedOnTheEndOfABusTakesTheBusWidth() {
        Component adder = place(openDocument, StandardComponentTypes.ADDER, 0, 0, Map.of(WIDTH, 8));
        GridPoint sum = pin(openDocument, adder.id(), "S");
        addWire(openDocument, sum.x(), sum.y(), sum.x() + 6, sum.y());

        Component outputPin = ComponentPlacement.place(context,
                openDocument.registry().require(StandardComponentTypes.OUTPUT_PIN),
                new GridPoint(sum.x() + 6, sum.y() - 1), Rotation.R0);

        assertThat(widthOf(openDocument, outputPin.id())).isEqualTo(8);

        openDocument.history().undo();

        assertThat(openDocument.document().component(outputPin.id())).isEmpty();
    }

    @Test
    void aConstantDraggedOntoABusTakesItsWidthAndTheStatusLineSaysSo() {
        Component adder = place(openDocument, StandardComponentTypes.ADDER, 20, 0, Map.of(WIDTH, 8));
        GridPoint input = pin(openDocument, adder.id(), "A");
        addWire(openDocument, 10, input.y(), input.x(), input.y());
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 0, 20, Map.of(WIDTH, 1));
        GridPoint start = pin(openDocument, constant.id(), "out");

        context.selectionMoves().move(openDocument, Set.of(constant.id()), 10 - start.x(), input.y() - start.y(),
                true);

        assertThat(widthOf(openDocument, constant.id())).isEqualTo(8);
        assertThat(context.notices).contains("Constante tomó el ancho de lo conectado: 8 bits");

        openDocument.history().undo();

        assertThat(widthOf(openDocument, constant.id())).isEqualTo(1);
        assertThat(pin(openDocument, constant.id(), "out")).isEqualTo(start);
    }

    @Test
    void aRunOfArrowKeysFollowsTheWidthWhereItEndsAsOneUndoStep() {
        Component adder = place(openDocument, StandardComponentTypes.ADDER, 20, 0, Map.of(WIDTH, 8));
        GridPoint input = pin(openDocument, adder.id(), "A");
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 0, input.y() - 2, Map.of(WIDTH, 1));
        GridPoint start = pin(openDocument, constant.id(), "out");
        addWire(openDocument, start.x() + 2, input.y(), input.x(), input.y());
        int stepsBefore = undoSteps();

        context.selectionMoves().nudge(openDocument, Set.of(constant.id()), 2, 0);
        context.selectionMoves().nudge(openDocument, Set.of(constant.id()), 0, 1);

        assertThat(widthOf(openDocument, constant.id())).isEqualTo(8);
        assertThat(undoSteps()).isEqualTo(stepsBefore + 1);

        openDocument.history().undo();

        assertThat(widthOf(openDocument, constant.id())).isEqualTo(1);
        assertThat(pin(openDocument, constant.id(), "out")).isEqualTo(start);
    }

    private int undoSteps() {
        int steps = 0;
        while (openDocument.history().canUndo()) {
            openDocument.history().undo();
            steps++;
        }
        for (int step = 0; step < steps; step++) {
            openDocument.history().redo();
        }
        return steps;
    }

    @Test
    void anInterfacePinMovedOntoAnEightBitPinInsideItsTabTakesEightBitsAndSoDoesTheComponentPin() {
        OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.byteBuffer())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c5"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""))));
        OpenDocument body = circuit.openBody(ExampleDefinitions.BYTE_BUFFER, Optional.of("c5"));
        try (TestToolContext inside = new TestToolContext(body)) {
            ElementId output = new ElementId("c2");
            body.execute(new MoveElementsCommand(List.of(output), 6, 0));
            body.execute(new ChangeParametersCommand(output,
                    body.registry().require(StandardComponentTypes.OUTPUT_PIN), Map.of(WIDTH, 1)));

            inside.selectionMoves().move(body, Set.of(output), -6, 0, false);

            assertThat(widthOf(body, output)).isEqualTo(8);
            assertThat(circuit.document().definition(ExampleDefinitions.BYTE_BUFFER).orElseThrow().pinout())
                    .allMatch(pin -> pin.spec().width() == 8);

            circuit.history().undo();

            assertThat(widthOf(body, output)).isEqualTo(1);
        } finally {
            body.close();
            circuit.close();
        }
    }
}
