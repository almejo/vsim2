package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.InsideEditRefusedException;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.REFUSED_INSIDE_TEXT;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.refusedInside;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An edit refused inside a component is never silent and never an error dialog: the component tab says why in its
 * status line, and nothing changes. Without a tab (the AI) the place still throws. An interface edit of a component
 * used inside another is not refused (spec §6.2 c): the bodies that contain it follow.
 */
class InsideRefusalTest {

    /** The inverter's input pin part «a». */
    private static final ElementId INPUT_PIN = new ElementId("c1");

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private OpenDocument body;
    private EditorView inside;

    @BeforeEach
    void enterAnInverterUsedInsideADoubleInverter() {
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c1"),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), new GridPoint(0, 0), Rotation.R0,
                Map.of(), ""))));
        body = circuit.openBody(ExampleDefinitions.INVERTER, Optional.of("c1/c2"));
        inside = new EditorView(body, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
    }

    @AfterEach
    void closeAll() {
        inside.dispose();
        body.close();
        circuit.close();
    }

    @Test
    void aRefusedEditInsideIsExplainedInTheStatusLineAndChangesNothing() {
        ComponentDefinition before = circuit.document().definition(ExampleDefinitions.INVERTER).orElseThrow();
        Optional<Command> lastStep = circuit.history().undoCommand();

        body.execute(refusedInside());

        assertThat(inside.statusBar().text()).contains(REFUSED_INSIDE_TEXT);
        assertThat(circuit.document().definition(ExampleDefinitions.INVERTER)).contains(before);
        assertThat(circuit.history().undoCommand()).isEqualTo(lastStep);
    }

    @Test
    void deletingAnInterfacePinOfAComponentUsedInsideAnotherIsApplied() {
        inside.selection().set(List.of(INPUT_PIN));

        perform(inside.actions().delete());

        assertThat(body.document().component(INPUT_PIN)).isEmpty();
        assertThat(circuit.document().definition(ExampleDefinitions.INVERTER).orElseThrow().pinout()).hasSize(1);
        assertThat(inside.statusBar().text()).doesNotContain(REFUSED_INSIDE_TEXT);
    }

    @Test
    void withoutATabTheRefusalIsAnError() {
        OpenDocument bare = circuit.openBody(ExampleDefinitions.INVERTER, Optional.of("c1/c2"));
        try {
            assertThatThrownBy(() -> bare.execute(refusedInside())).isInstanceOf(InsideEditRefusedException.class);
        } finally {
            bare.close();
        }
    }

    @Test
    void disposingTheTabStopsExplaining() {
        inside.dispose();
        assertThatThrownBy(() -> body.execute(refusedInside())).isInstanceOf(InsideEditRefusedException.class);
        inside = new EditorView(body, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
    }
}
