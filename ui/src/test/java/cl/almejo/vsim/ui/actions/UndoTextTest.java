package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edición ▸ Deshacer / Rehacer name the place of a step made inside a component (spec §10.4), in every tab of the
 * circuit; a step of the circuit itself keeps its plain text.
 */
class UndoTextTest {

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private OpenDocument body;
    private EditorView main;
    private EditorView inside;

    @BeforeEach
    void enterAnInverter() {
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c5"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""))));
        body = circuit.openBody(ExampleDefinitions.INVERTER, Optional.of("c5"));
        main = new EditorView(circuit, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        inside = new EditorView(body, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
    }

    @AfterEach
    void closeAll() {
        inside.dispose();
        main.dispose();
        body.close();
        circuit.close();
    }

    @Test
    void undoOfAnInsideEditNamesThePlace() {
        assertThat(nameOf(main.actions().undo())).isEqualTo("Deshacer agregar");

        body.execute(new AddElementsCommand(List.of(StandardComponentTypes.registry()
                .require(StandardComponentTypes.BUFFER).newComponent(new ElementId("c9"), new GridPoint(20, 20),
                        Rotation.R0, Map.of(), ""))));

        assertThat(nameOf(main.actions().undo())).isEqualTo("Deshacer agregar (en Inversor)");
        assertThat(nameOf(inside.actions().undo())).isEqualTo("Deshacer agregar (en Inversor)");
        circuit.history().undo();
        assertThat(nameOf(main.actions().redo())).isEqualTo("Rehacer agregar (en Inversor)");
        assertThat(nameOf(inside.actions().redo())).isEqualTo("Rehacer agregar (en Inversor)");
        assertThat(nameOf(main.actions().undo())).isEqualTo("Deshacer agregar");
    }

    private static String nameOf(Action action) {
        return (String) action.getValue(Action.NAME);
    }
}
