package cl.almejo.vsim.ui.window;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/** A component tab showing no instance («Sin valores», spec §6.3) says so in its status line, and only then. */
class NoValuesHintTest {

    private static final String HINT = "Sin valores: elige una pieza en “Valores de”";

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void closeAll() {
        circuit.close();
    }

    @Test
    void aTabWithoutValuesSaysWhereToChooseThem() {
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c5"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), new GridPoint(0, 0), Rotation.R0, Map.of(),
                ""))));
        OpenDocument body = circuit.openBody(ExampleDefinitions.INVERTER, Optional.empty());
        EditorView inside = new EditorView(body, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        EditorView main = new EditorView(circuit, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        try {
            assertThat(inside.statusBar().text()).contains(HINT);
            assertThat(main.statusBar().text()).doesNotContain(HINT);

            body.setContextPath(Optional.of("c5"));
            assertThat(inside.statusBar().text()).doesNotContain(HINT);

            body.setContextPath(Optional.empty());
            assertThat(inside.statusBar().text()).contains(HINT);
        } finally {
            main.dispose();
            inside.dispose();
            body.close();
        }
    }
}
