package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static cl.almejo.vsim.ui.library.TemplateFixture.perform;
import static org.assertj.core.api.Assertions.assertThat;

/** Renaming the pins in the «Crear componente» dialog (agreed with the user 2026-10-18). */
class RenamePinsCreationTest {

    @TempDir
    Path directory;

    private TemplateFixture fixture;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        fixture = new TemplateFixture(directory.resolve("library"));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private Set<DocumentElement> elements() {
        return Set.copyOf(fixture.openDocument.document().elements());
    }

    private ComponentDefinition onlyDefinition() {
        return fixture.openDocument.document().definitions().get(0);
    }

    private static List<String> pinNames(ComponentDefinition definition) {
        return definition.pinout().stream().map(pin -> pin.spec().name()).toList();
    }

    private Component instance() {
        return fixture.openDocument.document().components().stream()
                .filter(component -> SubcircuitTypes.definitionIdOf(component.typeId()).isPresent())
                .findFirst()
                .orElseThrow();
    }

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(fixture.openDocument.document().newWireId(), new GridPoint(startX, startY),
                new GridPoint(endX, endY));
        fixture.openDocument.execute(new AddElementsCommand(List.of(wire)));
        return wire;
    }

    /** Switch «Cin» (0, 1) → NOT (6, 0) → LED «S» (11, -1), all selected; returns the switch, the NOT and the LED. */
    private List<Component> drawSwitchNotLed() {
        Component input = fixture.place(StandardComponentTypes.SWITCH, 0, 1, "Cin");
        Wire first = wire(4, 2, 6, 2);
        Component gate = fixture.place(StandardComponentTypes.NOT, 6, 0, "");
        Wire second = wire(10, 2, 12, 2);
        Component led = fixture.place(StandardComponentTypes.LED, 11, -1, "S");
        fixture.view.canvas().selection().set(List.of(input.id(), first.id(), gate.id(), second.id(), led.id()));
        return List.of(input, gate, led);
    }

    @Test
    void renamedPinsNameTheDefinitionTheInstanceAndTheLibraryFile() throws Exception {
        fixture.drawInverter(0, 0);
        fixture.dialogs.acceptForm(form -> {
            form.setName("Negador");
            form.renameInput(0, "Dato");
            form.renameOutput(0, "Negado");
        });

        perform(fixture.features.actions().createComponent());

        ComponentDefinition definition = onlyDefinition();
        assertThat(pinNames(definition)).containsExactly("Dato", "Negado");
        assertThat(definition.body().components()).filteredOn(component -> InterfacePins.isInterface(
                component.typeId())).extracting(Component::label).containsExactlyInAnyOrder("Dato", "Negado");
        assertThat(fixture.openDocument.registry().typeOf(instance()).pins()).extracting(PinSpec::name)
                .containsExactly("Dato", "Negado");
        DefinitionBundle saved = fixture.service.read(fixture.service.fileOf(definition.id()));
        assertThat(saved.root()).isEqualTo(definition);
        assertThat(pinNames(saved.root())).containsExactly("Dato", "Negado");
    }

    @Test
    void withoutReplacingTheUsersPinsKeepTheirLabels() {
        fixture.drawInverter(0, 0);
        Set<DocumentElement> before = elements();
        fixture.dialogs.acceptForm(form -> {
            form.setName("Negador");
            form.setReplaceSelection(false);
            form.renameInput(0, "Dato");
        });

        perform(fixture.features.actions().createComponent());

        assertThat(elements()).isEqualTo(before);
        assertThat(pinNames(onlyDefinition())).containsExactly("Dato", "y");
    }

    @Test
    void convertedTestPartPinsAreRenamedOnlyInTheDefinition() {
        List<Component> parts = drawSwitchNotLed();
        fixture.dialogs.acceptForm(form -> {
            form.setName("Negador");
            form.renameInput(0, "Entrada");
            form.renameOutput(0, "Salida");
        });

        perform(fixture.features.actions().createComponent());

        assertThat(pinNames(onlyDefinition())).containsExactly("Entrada", "Salida");
        // The switch and the LED stay in the circuit with their own labels, still connected to the instance.
        assertThat(fixture.openDocument.document().component(parts.get(0).id())).contains(parts.get(0));
        assertThat(fixture.openDocument.document().component(parts.get(2).id())).contains(parts.get(2));
        Netlist netlist = new NetlistCompiler(fixture.openDocument.registry()).compile(
                fixture.openDocument.document());
        assertThat(netlist.netForPin(new PinRef(parts.get(0).id(), 0)).orElseThrow().pins())
                .contains(new PinRef(instance().id(), 0));
        assertThat(netlist.netForPin(new PinRef(parts.get(2).id(), 0)).orElseThrow().pins())
                .contains(new PinRef(instance().id(), 1));
    }

    @Test
    void volverKeepsTheNewNames() {
        Component led = drawSwitchNotLed().get(2);
        fixture.dialogs.acceptForm(form -> {
            form.setName("Mudo");
            form.renameInput(0, "Entrada");
            form.setConverted(led.id(), false);
        });
        fixture.dialogs.missingPinsAnswers.add(false);
        fixture.dialogs.acceptForm(form -> {
            assertThat(form.inputNames()).containsExactly("Entrada");
            form.setConverted(led.id(), true);
        });

        perform(fixture.features.actions().createComponent());

        assertThat(fixture.dialogs.forms).hasSize(2);
        assertThat(pinNames(onlyDefinition())).containsExactly("Entrada", "S");
    }

    @Test
    void anInvalidPinNameCreatesNothing() {
        fixture.drawInverter(0, 0);
        Set<DocumentElement> before = elements();
        fixture.dialogs.acceptForm(form -> {
            form.setName("Negador");
            form.renameOutput(0, "A");
        });

        perform(fixture.features.actions().createComponent());

        assertThat(fixture.dialogs.forms.get(0).problem()).contains("Hay más de un pin llamado «a».");
        assertThat(elements()).isEqualTo(before);
        assertThat(fixture.openDocument.document().definitions()).isEmpty();
    }

    @Test
    void updatingAComponentRenamesItsPinsToo() {
        fixture.drawInverter(0, 0);
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));
        perform(fixture.features.actions().createComponent());
        fixture.drawInverter(20, 0);
        fixture.dialogs.acceptForm(form -> form.renameOutput(0, "Negado"));

        perform(fixture.features.actions().updateComponent());

        assertThat(pinNames(fixture.openDocument.document().definition("negador").orElseThrow()))
                .containsExactly("a", "Negado");
    }
}
