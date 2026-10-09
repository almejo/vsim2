package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.RenderState;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static cl.almejo.vsim.ui.library.TemplateActionsTest.texts;
import static cl.almejo.vsim.ui.library.TemplateFixture.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TemplateEditingTest {

    private static final VisibleState LED_ZERO = new VisibleState.LedState(Value.ZERO);
    private static final VisibleState LED_ONE = new VisibleState.LedState(Value.ONE);

    @TempDir
    Path directory;

    private TemplateFixture fixture;
    private CircuitDocument document;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
        document = fixture.openDocument.document();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private JPopupMenu contribute(Component target) {
        JPopupMenu menu = new JPopupMenu();
        fixture.features.actions().contributor().contribute(menu, Optional.of(target),
                fixture.view.canvas().selection(), new Point2D.Double());
        return menu;
    }

    private static void click(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && item.getText().equals(text)) {
                item.getAction().actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED, ""));
                return;
            }
        }
        throw new AssertionError("no item " + text + " in " + texts(menu));
    }

    /** Draws an inverter at the origin and makes it the component "Negador" (instance at the origin, 6 × 4). */
    private Component createNegador() {
        fixture.drawInverter(0, 0);
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));
        perform(fixture.features.actions().createComponent());
        return fixture.single("subcircuit:negador");
    }

    @Test
    void disassemblingPutsTheBodyBackInOneUndoStep() {
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = fixture.place("subcircuit:inverter", 10, 10, "U1");
        fixture.select(instance);

        JPopupMenu menu = contribute(instance);
        assertThat(texts(menu)).containsExactly("Desarmar");
        click(menu, "Desarmar");

        assertThat(document.components()).extracting(Component::typeId, Component::position).containsExactly(
                tuple(InterfacePins.INPUT_PIN, new GridPoint(10, 11)),
                tuple(StandardComponentTypes.NOT, new GridPoint(12, 10)),
                tuple(InterfacePins.OUTPUT_PIN, new GridPoint(16, 11)));
        assertThat(fixture.view.canvas().selection().ids())
                .containsExactlyInAnyOrderElementsOf(document.components().stream().map(Component::id).toList());
        fixture.openDocument.history().undo();
        assertThat(document.components()).containsExactly(instance);
    }

    @Test
    void updatingANestedDefinitionRefreshesTheOuterInstancesInOneStep() {
        Component negador = createNegador();
        Component input = fixture.place(InterfacePins.INPUT_PIN, -2, 1, "a");
        Component output = fixture.place(InterfacePins.OUTPUT_PIN, 6, 1, "y");
        fixture.select(input, negador, output);
        fixture.dialogs.acceptForm(form -> form.setName("Envoltura"));
        perform(fixture.features.actions().createComponent());
        assertThat(fixture.single("subcircuit:envoltura").position()).isEqualTo(new GridPoint(-2, 0));
        fixture.place(StandardComponentTypes.SWITCH, -6, 1, Map.of("value", "1"));
        Component led = fixture.place(StandardComponentTypes.LED, 3, -1, Map.of());
        waitUntil(() -> fixture.stateOf(led).equals(LED_ZERO));
        ComponentDefinition before = document.definition("negador").orElseThrow();

        fixture.drawPassThrough(20, 0);
        assertThat(fixture.features.actions().updateComponent().isEnabled()).isTrue();
        fixture.dialogs.acceptForm(form -> form.setTarget(before));
        perform(fixture.features.actions().updateComponent());

        assertThat(document.definition("negador")).get().isNotEqualTo(before);
        assertThat(fixture.openDocument.history().undoDescription()).contains("command.updateDefinition");
        waitUntil(() -> fixture.stateOf(led).equals(LED_ONE));
        fixture.openDocument.history().undo();
        assertThat(document.definition("negador")).contains(before);
        assertThat(fixture.openDocument.history().undoDescription()).isNotEqualTo(Optional.of("command.updateDefinition"));
        waitUntil(() -> fixture.stateOf(led).equals(LED_ZERO));
    }

    @Test
    void disassemblingARotatedInstancePutsTheBodyUnrotatedAtItsTopLeft() {
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        ComponentType type = fixture.openDocument.registry().require("subcircuit:inverter");
        Component instance = type.newComponent(document.newComponentId(), new GridPoint(10, 10), Rotation.R90,
                Map.of(), "U1");
        fixture.openDocument.execute(new AddElementsCommand(List.of(instance)));
        Bounds instanceBounds = type.bounds(instance);
        fixture.select(instance);

        click(contribute(instance), "Desarmar");

        int x = instanceBounds.x();
        int y = instanceBounds.y();
        assertThat(document.components()).extracting(Component::typeId, Component::position, Component::rotation)
                .containsExactly(
                        tuple(InterfacePins.INPUT_PIN, new GridPoint(x, y + 1), Rotation.R0),
                        tuple(StandardComponentTypes.NOT, new GridPoint(x + 2, y), Rotation.R0),
                        tuple(InterfacePins.OUTPUT_PIN, new GridPoint(x + 6, y + 1), Rotation.R0));
        assertThat(fixture.openDocument.history().undoDescription()).contains("command.disassemble");
        fixture.openDocument.history().undo();
        assertThat(document.components()).containsExactly(instance);
    }

    @Test
    void updatingWithTheComponentNestedInsideAnotherIsRefused() {
        Component negador = createNegador();
        Component input = fixture.place(InterfacePins.INPUT_PIN, -2, 1, "a");
        Component output = fixture.place(InterfacePins.OUTPUT_PIN, 6, 1, "y");
        fixture.select(input, negador, output);
        fixture.dialogs.acceptForm(form -> form.setName("Envoltura"));
        perform(fixture.features.actions().createComponent());
        Component envoltura = fixture.single("subcircuit:envoltura");
        Component outerInput = fixture.place(InterfacePins.INPUT_PIN, -4, 1, "b");
        Component outerOutput = fixture.place(InterfacePins.OUTPUT_PIN, 6, 1, "z");
        fixture.select(outerInput, envoltura, outerOutput);
        ComponentDefinition before = document.definition("negador").orElseThrow();
        Optional<String> lastStep = fixture.openDocument.history().undoDescription();

        fixture.dialogs.acceptForm(form -> form.setTarget(before));
        perform(fixture.features.actions().updateComponent());

        assertThat(fixture.dialogs.errors).containsExactly(Messages.text("template.update.recursive"));
        assertThat(document.definition("negador")).contains(before);
        assertThat(fixture.openDocument.history().undoDescription()).isEqualTo(lastStep);
    }

    @Test
    void updatingWithTheComponentItselfInsideIsRefused() {
        Component negador = createNegador();
        Component input = fixture.place(InterfacePins.INPUT_PIN, -2, 1, "a");
        Component output = fixture.place(InterfacePins.OUTPUT_PIN, 6, 1, "y");
        fixture.select(input, negador, output);
        ComponentDefinition before = document.definition("negador").orElseThrow();
        Optional<String> lastStep = fixture.openDocument.history().undoDescription();

        fixture.dialogs.acceptForm(form -> form.setTarget(before));
        perform(fixture.features.actions().updateComponent());

        assertThat(fixture.dialogs.errors).containsExactly(Messages.text("template.update.recursive"));
        assertThat(document.definition("negador")).contains(before);
        assertThat(document.components()).hasSize(3);
        assertThat(fixture.openDocument.history().undoDescription()).isEqualTo(lastStep);
    }

    @Test
    void updatingRewritesTheLibraryFileOnlyForLibraryComponents() throws Exception {
        createNegador();
        fixture.drawPassThrough(20, 0);
        fixture.dialogs.acceptForm(form -> {
        });
        perform(fixture.features.actions().updateComponent());
        ComponentDefinition updated = document.definition("negador").orElseThrow();
        assertThat(fixture.service.read(fixture.service.fileOf("negador")))
                .isEqualTo(new DefinitionBundle(updated, List.of()));

        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        fixture.drawPassThrough(30, 0);
        fixture.dialogs.acceptForm(form -> form.setTarget(ExampleDefinitions.inverter()));
        perform(fixture.features.actions().updateComponent());

        assertThat(document.definition("inverter")).get().isNotEqualTo(ExampleDefinitions.inverter());
        assertThat(fixture.library.find("inverter")).isEmpty();
    }

    @Test
    void theMenusOfferUpdatingOnlyWhenThereIsADefinitionToUpdate() {
        assertThat(texts(fixture.view.menu(MenuId.EDIT))).contains("Actualizar componente…");
        List<Component> parts = fixture.drawInverter(0, 0);
        assertThat(fixture.features.actions().updateComponent().isEnabled()).isFalse();
        assertThat(texts(contribute(parts.get(0)))).containsExactly("Crear componente desde selección…");

        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));

        assertThat(fixture.features.actions().updateComponent().isEnabled()).isTrue();
        assertThat(texts(contribute(parts.get(0))))
                .containsExactly("Crear componente desde selección…", "Actualizar componente…");
    }

    @Test
    void aDifferentLibraryVersionShowsTheBadgeAndCanBeTakenInOneStep() throws Exception {
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = fixture.place("subcircuit:inverter", 10, 10, "");
        fixture.select(instance);
        LibraryVersionBadge badge = fixture.features.badge();
        assertThat(badge.outdatedIds()).isEmpty();

        DefinitionBundle renamed = new DefinitionBundle(ExampleDefinitions.inverter().withName("Negador"), List.of());
        fixture.service.save(renamed);

        assertThat(badge.outdatedIds()).containsExactly(ExampleDefinitions.INVERTER);
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        badge.paint(graphics, new RenderState(document, Netlist.EMPTY, 0, SimulationSnapshot.EMPTY, Set.of(), 0));
        graphics.dispose();
        assertThat(image.getRGB(108, 84) >>> 24).isNotZero(); // top-right corner of the 4 × 4 box at (10, 10)

        JPopupMenu menu = contribute(instance);
        assertThat(texts(menu)).containsExactly("Desarmar", "Actualizar desde la librería");
        click(menu, "Actualizar desde la librería");

        assertThat(document.definition(ExampleDefinitions.INVERTER)).contains(renamed.root());
        assertThat(badge.outdatedIds()).isEmpty();
        fixture.openDocument.history().undo();
        assertThat(document.definition(ExampleDefinitions.INVERTER)).contains(ExampleDefinitions.inverter());
        assertThat(badge.outdatedIds()).containsExactly(ExampleDefinitions.INVERTER);
    }
}
