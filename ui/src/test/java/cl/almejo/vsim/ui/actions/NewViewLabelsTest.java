package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.RenameCircuitCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** «Nueva vista de «X»»: Ventana's two new-view items say what the view shows (the circuit, a machine). */
class NewViewLabelsTest {

    private final List<OpenDocument> opened = new ArrayList<>();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @AfterEach
    void close() {
        opened.forEach(OpenDocument::close);
    }

    private OpenDocument circuit() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        opened.add(document);
        return document;
    }

    private static EditorActions actionsOf(OpenDocument place) {
        Supplier<CircuitCanvas> noPane = () -> null;
        return new EditorActions(place, noPane, new Selection(), new ToolChoice() {
            @Override public void choose(Supplier<? extends Tool> newTool) { }
            @Override public void useDefault() { }
        }, UiTestSupport.NO_WINDOW, component -> { });
    }

    private static String name(Action action) {
        return (String) action.getValue(Action.NAME);
    }

    @Test
    void namesTheCircuitInTheCircuitsTab() {
        OpenDocument document = circuit();
        document.execute(RenameCircuitCommand.renaming(document.document(), "Sumador").orElseThrow());

        EditorActions actions = actionsOf(document);

        assertThat(name(actions.newView())).isEqualTo("Nueva vista de «Sumador»");
        assertThat(name(actions.newWindowWithCircuit())).isEqualTo("Nueva vista de «Sumador» en otra ventana");
    }

    @Test
    void followsARenameOfTheCircuit() {
        OpenDocument document = circuit();
        EditorActions actions = actionsOf(document);

        document.execute(RenameCircuitCommand.renaming(document.document(), "Contador").orElseThrow());

        assertThat(name(actions.newView())).isEqualTo("Nueva vista de «Contador»");
    }

    @Test
    void namesTheMachineInsideAMachine() {
        OpenDocument document = circuit();
        document.execute(new PutDefinitionsCommand(List.of(MachineDefinitions.create("maquina-abc123", "Semáforo"))));
        UiTestSupport.place(document, SubcircuitTypes.typeIdOf("maquina-abc123"), 0, 0, Map.of());
        OpenDocument body = document.openBody("maquina-abc123", Optional.of("c1"));
        opened.add(body);

        EditorActions actions = actionsOf(body);

        assertThat(name(actions.newView())).isEqualTo("Nueva vista de «Semáforo»");
    }

    @Test
    void aLongNameIsCutWithAnEllipsis() {
        OpenDocument document = circuit();
        String longName = "Contador sincrónico de cuatro bits con reinicio";
        document.execute(RenameCircuitCommand.renaming(document.document(), longName).orElseThrow());

        String label = name(actionsOf(document).newView());

        assertThat(label).startsWith("Nueva vista de «Contador sincrónico").endsWith("…»");
        assertThat(label).doesNotContain("reinicio");
    }

    @Test
    void speaksEnglishToo() {
        Messages.initialize(java.util.Locale.ENGLISH);
        OpenDocument document = circuit();
        document.execute(RenameCircuitCommand.renaming(document.document(), "Adder").orElseThrow());

        EditorActions actions = actionsOf(document);

        assertThat(name(actions.newView())).isEqualTo("New view of “Adder”");
        assertThat(name(actions.newWindowWithCircuit())).isEqualTo("New view of “Adder” in another window");
    }
}
