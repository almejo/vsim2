package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Problemas with component tabs (spec §6.2 a, §6.7): a piece whose new pin went elsewhere is listed by its name, a
 * component tab lists the circuit's problems, and F8 on a problem inside a component enters it with that instance's
 * values and selects the part.
 */
class ProblemsInsideTest {

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<String> wentTo = new ArrayList<>();
    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }

        @Override
        public boolean goToPart(OpenDocument document, Optional<String> definitionId, Optional<String> contextPath,
                                ElementId part) {
            assertThat(document).isSameAs(circuit);
            wentTo.add(definitionId.orElse("-") + " " + contextPath.orElse("-") + " " + part.value());
            return true;
        }
    };
    private final List<EditorView> views = new ArrayList<>();
    private final List<OpenDocument> bodies = new ArrayList<>();

    @AfterEach
    void closeAll() {
        views.forEach(EditorView::dispose);
        bodies.forEach(OpenDocument::close);
        circuit.close();
    }

    @Test
    void keptOldShapeIsListedWithTheInstanceName() {
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        place("c20", SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 10, 10);
        circuit.execute(new AddElementsCommand(List.of(new Wire(new ElementId("w90"), new GridPoint(6, 14),
                new GridPoint(10, 14)))));
        EditorView main = view(circuit);
        OpenDocument body = body(ExampleDefinitions.INVERTER, Optional.of("c20"));

        body.execute(new AddElementsCommand(List.of(StandardComponentTypes.registry().require(InterfacePins.INPUT_PIN)
                .newComponent(body.document().newComponentId(), new GridPoint(0, 10), Rotation.R0, Map.of(), "b"))));

        assertThat(main.problems().panel().problemTexts())
                .contains("Inversor: el pin b quedó en otro lugar (su lugar estaba ocupado)");
        circuit.history().undo();
        assertThat(main.problems().panel().problemTexts())
                .noneMatch(text -> text.contains("quedó en otro lugar"));
    }

    @Test
    void whatCouldNotFollowInsideAContainingBodyIsListedWithItsRoute() {
        // the double inverter's second inverter (c3, at 6,0) cannot take a new input at (6,4): a wire ends there
        ComponentDefinition plain = ExampleDefinitions.doubleInverter();
        ComponentDefinition busy = new ComponentDefinition(plain.id(), plain.name(), "", plain.size(),
                plain.pinout(), new CircuitFragment(plain.body().components(), List.of(new Wire(new ElementId("w90"),
                new GridPoint(6, 4), new GridPoint(8, 4))), List.of()));
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(), busy)));
        place("c31", SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER), 30, 10);
        EditorView main = view(circuit);
        OpenDocument body = body(ExampleDefinitions.INVERTER, Optional.of("c31/c2"));
        EditorView inside = view(body);

        body.execute(new AddElementsCommand(List.of(StandardComponentTypes.registry().require(InterfacePins.INPUT_PIN)
                .newComponent(body.document().newComponentId(), new GridPoint(0, 10), Rotation.R0, Map.of(), "b"))));

        String expected = "Doble inversor › Inversor 2: el pin b quedó en otro lugar (su lugar estaba ocupado)";
        assertThat(main.problems().panel().problemTexts()).contains(expected);
        assertThat(inside.problems().panel().problemTexts()).contains(expected);
        circuit.history().undo();
        assertThat(main.problems().panel().problemTexts()).noneMatch(text -> text.contains("quedó en otro lugar"));
    }

    @Test
    void bodyTabListsTheParentsProblems() {
        circuit.execute(new PutDefinitionsCommand(List.of(leaf())));
        place("c5", SubcircuitTypes.typeIdOf("hoja"), 0, 0);
        place("c9", SubcircuitTypes.typeIdOf("hoja"), 20, 0);
        EditorView main = view(circuit);
        EditorView inside = view(body("hoja", Optional.of("c5")));

        assertThat(main.problems().panel().problemTexts()).hasSize(2);
        assertThat(inside.problems().panel().problemTexts())
                .containsExactlyElementsOf(main.problems().panel().problemTexts());
    }

    @Test
    void f8OnANestedProblemEntersWithTheContext() {
        circuit.execute(new PutDefinitionsCommand(List.of(leaf(), box())));
        place("c5", SubcircuitTypes.typeIdOf("caja"), 0, 0);
        EditorView main = view(circuit);

        main.problems().navigator().next();

        assertThat(wentTo).containsExactly("caja c5 c2");
    }

    @Test
    void f8InAComponentTabOnAProblemOfTheCircuitGoesToTheCircuitsTab() {
        circuit.execute(new PutDefinitionsCommand(List.of(leaf())));
        place("c5", SubcircuitTypes.typeIdOf("hoja"), 0, 0);
        EditorView inside = view(body("hoja", Optional.of("c5")));

        inside.problems().navigator().next();

        assertThat(wentTo).containsExactly("- - c5");
    }

    @Test
    void contentsKeptForAMemoryNoLongerInTheComponentAreListedAndForgottenFromTheRow() {
        // debt of task 9b: an orphan entry stayed in the file, ignored and invisible
        circuit.execute(new PutDefinitionsCommand(List.of(smallMemory())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c5"),
                SubcircuitTypes.typeIdOf("memoria-chica"), new GridPoint(10, 10), Rotation.R0, Map.of(), "")
                .withMemoryContents(Map.of("c99", "@0 1")))));
        EditorView main = view(circuit);
        String expected = "Memoria chica: guarda contenido propio de una memoria que ya no está en el componente (c99)";

        assertThat(main.problems().panel().problemTexts()).containsExactly(expected);
        List<javax.swing.Action> actions = main.problems().panel().actionsAt(0);
        assertThat(actions).extracting(action -> action.getValue(javax.swing.Action.NAME))
                .containsExactly("Olvidar ese contenido");
        actions.getFirst().actionPerformed(null);

        assertThat(circuit.document().component(new ElementId("c5")).orElseThrow().memoryContents()).isEmpty();
        assertThat(main.problems().panel().problemTexts()).isEmpty();
        circuit.history().undo();
        assertThat(main.problems().panel().problemTexts()).containsExactly(expected);
    }

    /** «Memoria chica»: a RAM c10 inside, no pins. */
    private static ComponentDefinition smallMemory() {
        Component ram = StandardComponentTypes.registry().require(StandardComponentTypes.RAM)
                .newComponent(new ElementId("c10"), new GridPoint(0, 0), Rotation.R0, Map.of(), "");
        return new ComponentDefinition("memoria-chica", "Memoria chica", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(ram), List.of(), List.of()));
    }

    private EditorView view(OpenDocument place) {
        EditorView view = new EditorView(place, PainterRegistry.standard(), ColorScheme.LIGHT, window);
        views.add(view);
        return view;
    }

    private OpenDocument body(String definitionId, Optional<String> context) {
        OpenDocument body = circuit.openBody(definitionId, context);
        bodies.add(body);
        return body;
    }

    private void place(String id, String typeId, int x, int y) {
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId(id), typeId, new GridPoint(x, y),
                Rotation.R0, Map.of(), ""))));
    }

    /** «hoja»: an input pin «a» that nothing inside uses (each piece of it shows an unconnected terminal). */
    private static ComponentDefinition leaf() {
        Component input = StandardComponentTypes.registry().require(InterfacePins.INPUT_PIN)
                .newComponent(new ElementId("c1"), new GridPoint(0, 10), Rotation.R0, Map.of(), "a");
        return new ComponentDefinition("hoja", "Hoja", "", new GridSize(4, 4),
                List.of(new DefinitionPin(PinSpec.input("a", 0, 2), input.id())),
                new CircuitFragment(List.of(input), List.of(), List.of()));
    }

    /** «caja»: one «hoja» c2 inside and nothing else, so the problem of c2 is nested: {@code c5/c2}. */
    private static ComponentDefinition box() {
        Component nested = new Component(new ElementId("c2"), SubcircuitTypes.typeIdOf("hoja"), new GridPoint(4, 4),
                Rotation.R0, Map.of(), "");
        return new ComponentDefinition("caja", "Caja", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(nested), List.of(), List.of()));
    }
}
