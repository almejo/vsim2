package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static cl.almejo.vsim.ui.library.TemplateActionsTest.texts;
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

    /** A switch wired to the input of an inverter instance at (10, 10) and a LED wired to its output. */
    private record Wired(Component instance, Component source, Component led) {
    }

    private Wired wiredInverter(String switchValue) {
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = fixture.place("subcircuit:inverter", 10, 10, "");
        ComponentTypeRegistry registry = fixture.openDocument.registry();
        ComponentType type = registry.require(instance.typeId());
        GridPoint input = type.pinLocation(instance, 0);
        GridPoint output = type.pinLocation(instance, 1);
        GridPoint switchPin = new GridPoint(input.x() - 6, input.y());
        GridPoint ledPin = new GridPoint(output.x() + 6, output.y());
        Component source = placeWithPinAt(StandardComponentTypes.SWITCH, switchPin, Map.of("value", switchValue));
        Component led = placeWithPinAt(StandardComponentTypes.LED, ledPin, Map.of());
        wire(switchPin, input);
        wire(output, ledPin);
        return new Wired(instance, source, led);
    }

    private Component placeWithPinAt(String typeId, GridPoint pin, Map<String, Object> parameters) {
        GridPoint offset = fixture.openDocument.registry().require(typeId).pin(0).position();
        return fixture.place(typeId, pin.x() - offset.x(), pin.y() - offset.y(), parameters);
    }

    private void wire(GridPoint start, GridPoint end) {
        fixture.openDocument.execute(new AddWireCommand(new Wire(document.newWireId(), start, end)));
    }

    private Netlist netlist() {
        return new NetlistCompiler(fixture.openDocument.registry()).compile(document);
    }

    @Test
    void disassemblingKeepsTheOutsideConnections() {
        Wired wired = wiredInverter("1");
        waitUntil(() -> fixture.stateOf(wired.led()).equals(LED_ZERO));
        List<Component> componentsBefore = document.components();
        List<Wire> wiresBefore = document.wires();
        fixture.select(wired.instance());

        click(contribute(wired.instance()), "Desarmar");

        assertThat(fixture.dialogs.messages).isEmpty();
        Component gate = fixture.single(StandardComponentTypes.BUFFER);
        Netlist netlist = netlist();
        PinRef switchPin = new PinRef(wired.source().id(), 0);
        PinRef ledPin = new PinRef(wired.led().id(), 0);
        assertThat(netlist.netForPin(switchPin)).isNotEqualTo(netlist.netForPin(ledPin));
        assertThat(netlist.netForPin(switchPin).orElseThrow().pins()).contains(new PinRef(gate.id(), 0));
        assertThat(netlist.netForPin(ledPin).orElseThrow().pins()).contains(new PinRef(gate.id(), 1));
        fixture.openDocument.execute(new ChangeParametersCommand(wired.source().id(),
                fixture.openDocument.registry().require(StandardComponentTypes.SWITCH), Map.of("value", "0")));
        waitUntil(() -> fixture.stateOf(wired.led()).equals(LED_ONE));

        fixture.openDocument.history().undo(); // the switch
        fixture.openDocument.history().undo(); // Desarmar, in one step
        assertThat(document.components()).containsExactlyInAnyOrderElementsOf(componentsBefore);
        assertThat(document.wires()).containsExactlyInAnyOrderElementsOf(wiresBefore);
        waitUntil(() -> fixture.stateOf(wired.led()).equals(LED_ZERO));
    }

    @Test
    void disassemblingAWideTemplateKeepsTheWidthOfItsPinsAndBridgesTheOutside() {
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.byteBuffer())));
        Component instance = fixture.place("subcircuit:byte-buffer", 10, 10, "");
        ComponentType type = fixture.openDocument.registry().typeOf(instance);
        GridPoint input = type.pinLocation(instance, 0);
        GridPoint output = type.pinLocation(instance, 1);
        Map<String, Object> eightBits = Map.of(InterfacePins.WIDTH, 8);
        GridPoint sourcePin = new GridPoint(input.x() - 6, input.y());
        GridPoint sinkPin = new GridPoint(output.x() + 6, output.y());
        Component source = placeWithPinAt(InterfacePins.INPUT_PIN, sourcePin, eightBits);
        Component sink = placeWithPinAt(InterfacePins.OUTPUT_PIN, sinkPin, eightBits);
        wire(sourcePin, input);
        wire(output, sinkPin);
        fixture.select(instance);

        click(contribute(instance), "Desarmar");

        assertThat(fixture.dialogs.messages).isEmpty();
        List<Component> pasted = document.components().stream()
                .filter(component -> InterfacePins.isInterface(component.typeId()))
                .filter(component -> !component.id().equals(source.id()) && !component.id().equals(sink.id()))
                .toList();
        assertThat(pasted).hasSize(2).allSatisfy(component -> assertThat(InterfacePins.widthOf(component)).isEqualTo(8));
        Netlist netlist = netlist();
        Component pastedInput = pasted.stream().filter(component -> component.typeId().equals(InterfacePins.INPUT_PIN)).findFirst()
                .orElseThrow();
        Component pastedOutput = pasted.stream().filter(component -> component.typeId().equals(InterfacePins.OUTPUT_PIN)).findFirst()
                .orElseThrow();
        assertThat(netlist.netForPin(new PinRef(source.id(), 0)).orElseThrow().pins())
                .contains(new PinRef(pastedInput.id(), 0));
        assertThat(netlist.netForPin(new PinRef(sink.id(), 0)).orElseThrow().pins())
                .contains(new PinRef(pastedOutput.id(), 0));
    }

    @Test
    void disassemblingOntoAForeignWireIsRefused() {
        Wired wired = wiredInverter("1");
        wire(new GridPoint(12, 4), new GridPoint(12, 12)); // ends inside the box, where the body's input lands
        List<Component> componentsBefore = document.components();
        List<Wire> wiresBefore = document.wires();
        Optional<String> lastStep = fixture.openDocument.history().undoDescription();
        fixture.select(wired.instance());

        click(contribute(wired.instance()), "Desarmar");

        assertThat(fixture.dialogs.messages).containsExactly(Messages.format("template.notDisassembled",
                Messages.format("library.place.componentName", "Inversor")));
        assertThat(document.components()).containsExactlyElementsOf(componentsBefore);
        assertThat(document.wires()).containsExactlyElementsOf(wiresBefore);
        assertThat(fixture.openDocument.history().undoDescription()).isEqualTo(lastStep);
    }

    @Test
    void takingALibraryVersionThatWouldNestItselfIsRefused() throws Exception {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(inverter, ExampleDefinitions.doubleInverter())));
        Component instance = fixture.place("subcircuit:inverter", 10, 10, "");
        // The library's double inverter is plain, but its inverter nests it; this circuit's double inverter nests
        // the inverter, so taking the library's inverter would put the inverter inside itself.
        ComponentDefinition plainDouble = new ComponentDefinition(ExampleDefinitions.DOUBLE_INVERTER, "Doble",
                "", inverter.size(), inverter.pinout(), inverter.body());
        List<Component> body = new ArrayList<>(inverter.body().components());
        body.add(new Component(new ElementId("c99"), "subcircuit:" + ExampleDefinitions.DOUBLE_INVERTER,
                new GridPoint(20, 20), Rotation.R0, Map.of(), ""));
        ComponentDefinition nesting = new ComponentDefinition(inverter.id(), inverter.name(), inverter.description(),
                inverter.size(), inverter.pinout(),
                new CircuitFragment(body, inverter.body().wires(), inverter.body().labels()));
        fixture.service.save(new DefinitionBundle(nesting, List.of(plainDouble)));
        fixture.select(instance);
        Optional<String> lastStep = fixture.openDocument.history().undoDescription();

        JPopupMenu menu = contribute(instance);
        assertThat(texts(menu)).containsExactly("Desarmar", "Actualizar desde la librería");
        click(menu, "Actualizar desde la librería");

        assertThat(fixture.dialogs.errors).containsExactly(Messages.text("template.updateFromLibrary.recursive"));
        assertThat(document.definitions()).containsExactly(inverter, ExampleDefinitions.doubleInverter());
        assertThat(fixture.openDocument.history().undoDescription()).isEqualTo(lastStep);
    }

    @Test
    void takingALibraryVersionSaysWhichNestedVersionsTheCircuitKeeps() throws Exception {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter();
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(inverter, doubleInverter)));
        Component instance = fixture.place("subcircuit:" + ExampleDefinitions.DOUBLE_INVERTER, 10, 10, "");
        DefinitionBundle library = new DefinitionBundle(doubleInverter.withName("Doble"),
                List.of(inverter.withName("Negador")));
        fixture.service.save(library);
        fixture.select(instance);

        click(contribute(instance), "Actualizar desde la librería");

        assertThat(fixture.dialogs.messages).containsExactly(Messages.format("library.place.keptVersion",
                Messages.format("library.place.componentName", "Negador")));
        assertThat(document.definition(ExampleDefinitions.DOUBLE_INVERTER)).contains(library.root());
        assertThat(document.definition(ExampleDefinitions.INVERTER)).contains(inverter);
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
                tuple(StandardComponentTypes.BUFFER, new GridPoint(12, 10)),
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
        assertThat(fixture.openDocument.history().undoDescription())
                .isNotEqualTo(Optional.of("command.updateDefinition"));
        waitUntil(() -> fixture.stateOf(led).equals(LED_ZERO));
    }

    @Test
    void updatingAPlacedComponentWithANewPinKeepsItsWiresAndSaysWhereThePinWent() {
        Wired wired = wiredInverter("1");
        waitUntil(() -> fixture.stateOf(wired.led()).equals(LED_ZERO));
        ComponentDefinition before = document.definition(ExampleDefinitions.INVERTER).orElseThrow();
        List<Component> body = new ArrayList<>(fixture.drawInverter(40, 0));
        body.add(fixture.place(InterfacePins.INPUT_PIN, 40, 6, "b"));
        fixture.select(body.toArray(Component[]::new));
        fixture.dialogs.acceptForm(form -> form.setTarget(before));

        perform(fixture.features.actions().updateComponent());

        assertThat(document.definition(ExampleDefinitions.INVERTER).orElseThrow().pinout()).hasSize(3);
        assertThat(fixture.dialogs.messages).containsExactly(
                Messages.format("appearance.update.newPin.left", "b"));
        assertThat(netOfPinNamed(wired.instance(), "y").pins()).contains(new PinRef(wired.led().id(), 0));
        assertThat(netOfPinNamed(wired.instance(), "a").pins()).contains(new PinRef(wired.source().id(), 0));
        waitUntil(() -> fixture.stateOf(wired.led()).equals(LED_ZERO));
        fixture.openDocument.history().undo();
        assertThat(document.definition(ExampleDefinitions.INVERTER)).contains(before);
    }

    @Test
    void aLibraryVersionThatWouldTouchAnotherWireAsksFirstAndVolverChangesNothing() throws Exception {
        Wired wired = wiredInverter("1");
        wire(new GridPoint(16, 0), new GridPoint(16, 30));
        ComponentDefinition before = document.definition(ExampleDefinitions.INVERTER).orElseThrow();
        fixture.service.save(new DefinitionBundle(movedOutput(before), List.of()));
        List<Integer> asked = new ArrayList<>();
        fixture.features.actions().useConnectionsConfirmation(plan -> {
            asked.add(plan.keptOldShape().size());
            return false;
        });
        Optional<String> lastStep = fixture.openDocument.history().undoDescription();

        fixture.features.actions().updateFromLibrary(wired.instance());

        assertThat(asked).containsExactly(1);
        assertThat(document.definition(ExampleDefinitions.INVERTER)).contains(before);
        assertThat(fixture.openDocument.history().undoDescription()).isEqualTo(lastStep);
    }

    @Test
    void aLibraryVersionAppliedAnywayKeepsTheTouchingPieceInItsOldShapeAndSaysSo() throws Exception {
        Wired wired = wiredInverter("1");
        wire(new GridPoint(16, 0), new GridPoint(16, 30));
        ComponentDefinition before = document.definition(ExampleDefinitions.INVERTER).orElseThrow();
        fixture.service.save(new DefinitionBundle(movedOutput(before), List.of()));
        Runnable restore = fixture.features.actions().useConnectionsConfirmation(plan -> true);

        fixture.features.actions().updateFromLibrary(wired.instance());
        restore.run();

        assertThat(document.definition(ExampleDefinitions.INVERTER).orElseThrow().size().width()).isEqualTo(6);
        assertThat(document.component(wired.instance().id()).orElseThrow().appearance()).isPresent();
        assertThat(netOfPinNamed(wired.instance(), "y").pins()).contains(new PinRef(wired.led().id(), 0));
        assertThat(fixture.dialogs.messages).containsExactly(
                Messages.format("appearance.update.keptOldShape", wired.instance().id().value()));
    }

    /** {@code definition} 6 wide with its output «y» at (6,2), as a newer library version could bring it. */
    private static ComponentDefinition movedOutput(ComponentDefinition definition) {
        return new ComponentDefinition(definition.id(), definition.name(), "", new GridSize(6, 4), List.of(
                definition.pinout().getFirst(), new DefinitionPin(
                        PinSpec.output("y", 6, 2),
                        definition.pinout().getLast().source())), definition.body(), Optional.empty(),
                definition.revision() + 1);
    }

    private Net netOfPinNamed(Component instance, String name) {
        Component current = document.component(instance.id()).orElseThrow();
        List<PinSpec> pins = fixture.openDocument.registry().typeOf(current).pins();
        for (int index = 0; index < pins.size(); index++) {
            if (pins.get(index).name().equals(name)) {
                return netlist().netForPin(new PinRef(instance.id(), index)).orElseThrow();
            }
        }
        throw new AssertionError("no pin " + name);
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
                        tuple(StandardComponentTypes.BUFFER, new GridPoint(x + 2, y), Rotation.R0),
                        tuple(InterfacePins.OUTPUT_PIN, new GridPoint(x + 6, y + 1), Rotation.R0));
        assertThat(fixture.openDocument.history().undoDescription()).contains("command.disassemble");
        fixture.openDocument.history().undo();
        assertThat(document.components()).containsExactly(instance);
    }

    @Test
    void disassemblingABodyWhoseTunnelNameIsUsedOutsideRenamesItAndSaysSo() {
        ComponentTypeRegistry standard = StandardComponentTypes.registry();
        Component entrance = standard.require(StandardComponentTypes.TUNNEL_ENTRANCE).newComponent(
                new ElementId("c1"), new GridPoint(0, 0), Rotation.R0, Map.of("name", "A"), "");
        Component exit = standard.require(StandardComponentTypes.TUNNEL_EXIT).newComponent(
                new ElementId("c2"), new GridPoint(0, 4), Rotation.R0, Map.of("name", "A"), "");
        ComponentDefinition tunnels = new ComponentDefinition("tuneles", "Túneles", "",
                new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(entrance, exit), List.of(), List.of()));
        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(tunnels)));
        Component instance = fixture.place("subcircuit:tuneles", 40, 40, "U1");
        fixture.place(StandardComponentTypes.TUNNEL_ENTRANCE, 0, 0, Map.of("name", "A"));
        fixture.select(instance);

        click(contribute(instance), "Desarmar");

        assertThat(fixture.dialogs.messages).containsExactly(Messages.format("template.tunnelsRenamed",
                Messages.format("template.tunnelRename", "A", "A_2")));
        assertThat(document.components()).filteredOn(component -> component.typeId().startsWith("tunnel"))
                .extracting(component -> component.parameters().get("name"))
                .containsExactlyInAnyOrder("A", "A_2", "A_2");
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
