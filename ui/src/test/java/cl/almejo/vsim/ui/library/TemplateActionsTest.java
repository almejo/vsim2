package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static cl.almejo.vsim.ui.library.TemplateFixture.perform;
import static org.assertj.core.api.Assertions.assertThat;

class TemplateActionsTest {

    @TempDir
    Path directory;

    private TemplateFixture fixture;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    static List<String> texts(JMenu menu) {
        List<String> texts = new ArrayList<>();
        for (int index = 0; index < menu.getItemCount(); index++) {
            texts.add(menu.getItem(index) == null ? "---" : menu.getItem(index).getText());
        }
        return texts;
    }

    private JPopupMenu contribute(DocumentElement target) {
        JPopupMenu menu = new JPopupMenu();
        fixture.features.actions().contributor().contribute(menu, Optional.of(target),
                fixture.view.canvas().selection(), new Point2D.Double());
        return menu;
    }

    @Test
    void aSelectionWithoutInterfacePinsExplainsWhyAndOpensNothing() {
        assertThat(fixture.features.actions().createComponent().isEnabled()).isFalse();
        Component gate = fixture.place(StandardComponentTypes.NOT, 0, 0, "");
        fixture.select(gate);
        assertThat(fixture.features.actions().createComponent().isEnabled()).isTrue();

        perform(fixture.features.actions().createComponent());

        assertThat(fixture.dialogs.messages).containsExactly(Messages.text("template.noInterfacePins"));
        assertThat(fixture.dialogs.forms).isEmpty();
        assertThat(fixture.openDocument.document().definitions()).isEmpty();
    }

    @Test
    void creatingReplacesTheSelectionInOneUndoStepAndWritesTheLibrary() {
        fixture.drawInverter(0, 0);
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));

        perform(fixture.features.actions().createComponent());

        CircuitDocument document = fixture.openDocument.document();
        assertThat(fixture.dialogs.forms).singleElement().satisfies(form -> {
            assertThat(form.inputNames()).containsExactly("a");
            assertThat(form.outputNames()).containsExactly("y");
        });
        Component instance = document.components().get(0);
        assertThat(document.components()).hasSize(1);
        assertThat(instance.typeId()).isEqualTo("subcircuit:negador");
        assertThat(instance.position()).isEqualTo(GridPoint.ORIGIN);
        assertThat(fixture.view.canvas().selection().ids()).containsExactly(instance.id());
        assertThat(document.definition("negador")).isPresent();
        assertThat(fixture.library.find("negador")).isPresent();
        assertThat(directory.resolve("library").resolve("negador.vsimcomponent.json")).isRegularFile();

        fixture.openDocument.history().undo();
        assertThat(document.components()).hasSize(3);
        assertThat(document.definitions()).isEmpty();
        assertThat(fixture.library.find("negador")).isPresent();

        fixture.openDocument.history().redo();
        assertThat(document.components()).singleElement()
                .extracting(Component::typeId).isEqualTo("subcircuit:negador");
    }

    @Test
    void theNewComponentSimulatesLikeItsParts() {
        fixture.drawInverter(0, 0);
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));
        perform(fixture.features.actions().createComponent());

        fixture.place(StandardComponentTypes.SWITCH, -4, 1, Map.of("value", "1"));
        Component led = fixture.place(StandardComponentTypes.LED, 5, -1, Map.of());

        waitUntil(() -> fixture.stateOf(led).equals(new VisibleState.LedState(Value.ZERO)));
    }

    @Test
    void wiresThatCrossedTheSelectionEdgeEndOnTheInstancePins() {
        fixture.drawInverter(0, 0);
        Component source = fixture.place(StandardComponentTypes.SWITCH, -2, -5, Map.of("value", "0"));
        Component led = fixture.place(StandardComponentTypes.LED, 9, -1, Map.of());
        List<Wire> outside = List.of(
                new Wire(fixture.openDocument.document().newWireId(), new GridPoint(2, -4), new GridPoint(2, 2)),
                new Wire(fixture.openDocument.document().newWireId(), new GridPoint(6, 2), new GridPoint(10, 2)));
        fixture.openDocument.execute(new AddElementsCommand(outside));
        waitUntil(() -> fixture.stateOf(led).equals(new VisibleState.LedState(Value.ONE)));
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));

        perform(fixture.features.actions().createComponent());

        CircuitDocument document = fixture.openDocument.document();
        ComponentType switchType = fixture.openDocument.registry().require(StandardComponentTypes.SWITCH);
        assertThat(document.components()).extracting(Component::typeId)
                .containsExactlyInAnyOrder(StandardComponentTypes.SWITCH, StandardComponentTypes.LED,
                        "subcircuit:negador");
        assertThat(document.wires()).hasSize(3); // the two outside wires and the bridge to the instance input
        assertThat(fixture.dialogs.messages).isEmpty();
        assertThat(netOf(outside.get(0))).isNotEqualTo(netOf(outside.get(1)));
        fixture.openDocument.execute(new ChangeParametersCommand(source.id(), switchType, Map.of("value", "1")));
        waitUntil(() -> fixture.stateOf(led).equals(new VisibleState.LedState(Value.ZERO)));
        fixture.openDocument.execute(new ChangeParametersCommand(source.id(), switchType, Map.of("value", "0")));
        waitUntil(() -> fixture.stateOf(led).equals(new VisibleState.LedState(Value.ONE)));

        fixture.openDocument.history().undo(); // the two switch changes
        fixture.openDocument.history().undo();
        fixture.openDocument.history().undo(); // the creation, bridges included
        assertThat(document.wires()).hasSize(2);
        assertThat(document.components()).hasSize(5);
    }

    @Test
    void pinsThatCannotBeBridgedSafelyAreReportedAndNoOutsideNetsMerge() {
        Component input = fixture.place(InterfacePins.INPUT_PIN, 0, 0, "a");
        Component first = fixture.place(InterfacePins.OUTPUT_PIN, 6, 3, "y1");   // point (6,4)
        Component second = fixture.place(InterfacePins.OUTPUT_PIN, 6, 5, "y2");  // point (6,6)
        List<Wire> outside = List.of(
                new Wire(fixture.openDocument.document().newWireId(), new GridPoint(6, 4), new GridPoint(12, 4)),
                new Wire(fixture.openDocument.document().newWireId(), new GridPoint(6, 6), new GridPoint(12, 6)));
        fixture.openDocument.execute(new AddElementsCommand(outside));
        fixture.select(input, first, second);
        assertThat(netOf(outside.get(0))).isNotEqualTo(netOf(outside.get(1)));
        fixture.dialogs.acceptForm(form -> form.setName("Doble salida"));

        perform(fixture.features.actions().createComponent());

        assertThat(fixture.openDocument.document().wires()).containsExactlyInAnyOrderElementsOf(outside);
        assertThat(netOf(outside.get(0))).isNotEqualTo(netOf(outside.get(1)));
        assertThat(fixture.dialogs.messages)
                .containsExactly("Estos pines quedaron sin conectar: y1, y2. Conéctalos a mano.");
    }

    private NetId netOf(Wire wire) {
        return new NetlistCompiler(fixture.openDocument.registry()).compile(fixture.openDocument.document())
                .netForWire(wire.id()).orElseThrow().id();
    }

    @Test
    void keepingTheSelectionOnlyAddsTheDefinition() {
        List<Component> parts = fixture.drawInverter(0, 0);
        fixture.dialogs.acceptForm(form -> {
            form.setName("Negador");
            form.setReplaceSelection(false);
        });

        perform(fixture.features.actions().createComponent());

        CircuitDocument document = fixture.openDocument.document();
        assertThat(document.components()).containsExactlyElementsOf(parts);
        assertThat(document.definition("negador")).isPresent();
        fixture.openDocument.history().undo();
        assertThat(document.definitions()).isEmpty();
        assertThat(document.components()).hasSize(3);
    }

    @Test
    void cancellingChangesNothingAndATakenIdGetsANumber() throws Exception {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        fixture.service.save(new DefinitionBundle(new ComponentDefinition("negador", "Otro", "", inverter.size(),
                inverter.pinout(), inverter.body()), List.of()));
        fixture.drawInverter(0, 0);
        CircuitDocument document = fixture.openDocument.document();

        fixture.dialogs.cancelForm();
        perform(fixture.features.actions().createComponent());
        assertThat(document.definitions()).isEmpty();
        assertThat(document.components()).hasSize(3);

        fixture.dialogs.acceptForm(form -> form.setName("Negador"));
        perform(fixture.features.actions().createComponent());
        assertThat(document.definition("negador-2")).isPresent();
        assertThat(fixture.library.find("negador").orElseThrow().root().name()).isEqualTo("Otro");
    }

    @Test
    void aLibraryThatCannotBeWrittenIsReportedAndTheCircuitKeepsTheComponent() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "a file where the library directory should be");
        try (TemplateFixture broken = new TemplateFixture(blocked)) {
            broken.drawInverter(0, 0);
            broken.dialogs.acceptForm(form -> form.setName("Negador"));

            perform(broken.features.actions().createComponent());

            assertThat(broken.dialogs.errors).singleElement().asString().contains("negador.vsimcomponent.json");
            assertThat(broken.openDocument.document().definition("negador")).isPresent();
            assertThat(broken.library.entries()).isEmpty();
        }
    }

    @Test
    void theEditMenuTheContextMenuAndTheExtensionOfferTheAction() {
        assertThat(texts(fixture.view.menu(MenuId.EDIT))).contains("Crear componente desde selección…");
        List<Component> parts = fixture.drawInverter(0, 0);
        assertThat(texts(contribute(parts.get(1)))).containsExactly("Crear componente desde selección…");
        Component lone = fixture.place(StandardComponentTypes.NOT, 20, 0, "");
        fixture.select(lone);
        assertThat(texts(contribute(lone))).isEmpty();

        try (OpenDocument other = OpenDocument.createEmpty(StandardComponentTypes.registry())) {
            EditorView extended = new EditorView(other, fixture.services()
                    .withExtensions(List.of(new TemplatesExtension(fixture.service, fixture.dialogs))),
                    TemplateFixture.NO_WINDOW);
            assertThat(texts(extended.menu(MenuId.EDIT))).contains("Crear componente desde selección…");
            extended.dispose();
        }
    }
}
