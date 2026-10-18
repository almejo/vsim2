package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static cl.almejo.vsim.ui.library.TemplateFixture.perform;
import static org.assertj.core.api.Assertions.assertThat;

/** «Crear componente» with test parts in the selection (followups 2026-10-12 and 2026-10-18). */
class TestPartsCreationTest {

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

    private Set<DocumentElement> elements() {
        return Set.copyOf(fixture.openDocument.document().elements());
    }

    private ComponentDefinition onlyDefinition() {
        return fixture.openDocument.document().definitions().get(0);
    }

    @Test
    void aSelectionWithoutTestPartsGetsTodaysDialog() {
        fixture.drawInverter(0, 0);
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));

        perform(fixture.features.actions().createComponent());

        assertThat(fixture.dialogs.forms).singleElement()
                .satisfies(form -> assertThat(form.testParts()).isEmpty());
        assertThat(fixture.dialogs.missingPinsMessages).isEmpty();
    }

    @Test
    void testPartsBecomePinsAndTheReplacementIsOneUndoStep() {
        List<Component> parts = drawSwitchNotLed();
        Set<DocumentElement> before = elements();
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));

        perform(fixture.features.actions().createComponent());

        ComponentForm form = fixture.dialogs.forms.get(0);
        // By position: the LED (y = -1) comes before the switch (y = 1).
        assertThat(form.testParts()).extracting(part -> part.id()).containsExactly(parts.get(2).id(),
                parts.get(0).id());
        assertThat(form.inputNames()).containsExactly("Cin");
        assertThat(form.outputNames()).containsExactly("S");
        assertThat(onlyDefinition().pinout()).extracting(pin -> pin.spec().name()).containsExactly("Cin", "S");
        assertThat(onlyDefinition().body().components()).extracting(Component::typeId).containsExactlyInAnyOrder(
                InterfacePins.INPUT_PIN, StandardComponentTypes.NOT, InterfacePins.OUTPUT_PIN);
        // The switch and the LED stay in the circuit, connected to the instance; the NOT and the wires went inside.
        Component instance = instance();
        assertThat(fixture.openDocument.document().components()).containsExactlyInAnyOrder(parts.get(0),
                parts.get(2), instance);
        Netlist netlist = new NetlistCompiler(fixture.openDocument.registry()).compile(
                fixture.openDocument.document());
        assertThat(netlist.netForPin(new PinRef(parts.get(0).id(), 0)).orElseThrow().pins())
                .contains(new PinRef(instance.id(), indexOf(onlyDefinition().pinout(), "Cin")));
        assertThat(netlist.netForPin(new PinRef(parts.get(2).id(), 0)).orElseThrow().pins())
                .contains(new PinRef(instance.id(), indexOf(onlyDefinition().pinout(), "S")));
        assertThat(fixture.dialogs.missingPinsMessages).isEmpty();
        assertThat(fixture.dialogs.messages).isEmpty();

        fixture.openDocument.history().undo();

        assertThat(elements()).isEqualTo(before);
        assertThat(fixture.openDocument.document().definitions()).isEmpty();
    }

    @Test
    void withoutReplacingTheUsersCircuitKeepsItsTestParts() {
        drawSwitchNotLed();
        Set<DocumentElement> before = elements();
        fixture.dialogs.acceptForm(form -> {
            form.setName("Negador");
            form.setReplaceSelection(false);
        });

        perform(fixture.features.actions().createComponent());

        assertThat(elements()).isEqualTo(before);
        assertThat(onlyDefinition().pinout()).hasSize(2);
    }

    @Test
    void anUncheckedSwitchStaysInsideAsAConstant() {
        Component input = drawSwitchNotLed().get(0);
        fixture.dialogs.acceptForm(form -> {
            form.setName("Fijo");
            form.setConverted(input.id(), false);
        });

        perform(fixture.features.actions().createComponent());

        assertThat(onlyDefinition().pinout()).extracting(pin -> pin.spec().name()).containsExactly("S");
        assertThat(onlyDefinition().body().components()).extracting(Component::typeId)
                .contains(StandardComponentTypes.CONSTANT).doesNotContain(StandardComponentTypes.SWITCH);
        assertThat(fixture.dialogs.missingPinsMessages).isEmpty();
    }

    @Test
    void withoutOutputsItWarnsAndVolverGoesBackToTheSameDialog() {
        Component led = drawSwitchNotLed().get(2);
        fixture.dialogs.acceptForm(form -> {
            form.setName("Mudo");
            form.setConverted(led.id(), false);
        });
        fixture.dialogs.missingPinsAnswers.add(false);
        fixture.dialogs.acceptForm(form -> form.setConverted(led.id(), true));

        perform(fixture.features.actions().createComponent());

        assertThat(fixture.dialogs.missingPinsMessages).containsExactly(
                Messages.text("template.missingPins.outputs"));
        assertThat(fixture.dialogs.forms).hasSize(2);
        assertThat(fixture.dialogs.forms.get(1)).isSameAs(fixture.dialogs.forms.get(0));
        assertThat(onlyDefinition().name()).isEqualTo("Mudo");
        assertThat(onlyDefinition().pinout()).extracting(pin -> pin.spec().name()).containsExactly("Cin", "S");
    }

    @Test
    void withNoPinsAtAllItWarnsAndCrearIgualCreatesIt() {
        List<Component> parts = drawSwitchNotLed();
        fixture.dialogs.acceptForm(form -> {
            form.setName("Cerrado");
            form.setConverted(parts.get(0).id(), false);
            form.setConverted(parts.get(2).id(), false);
        });
        fixture.dialogs.missingPinsAnswers.add(true);

        perform(fixture.features.actions().createComponent());

        assertThat(fixture.dialogs.missingPinsMessages).containsExactly(Messages.text("template.missingPins.none"));
        assertThat(Messages.text("template.missingPins.none")).isEqualTo(
                "El componente no tendrá entradas ni salidas.");
        assertThat(onlyDefinition().pinout()).isEmpty();
        assertThat(fixture.openDocument.document().components()).hasSize(1);
    }

    @Test
    void volverThenCancelCreatesNothing() {
        Component led = drawSwitchNotLed().get(2);
        Set<DocumentElement> before = elements();
        fixture.dialogs.acceptForm(form -> {
            form.setName("Mudo");
            form.setConverted(led.id(), false);
        });
        fixture.dialogs.missingPinsAnswers.add(false);
        fixture.dialogs.cancelForm();

        perform(fixture.features.actions().createComponent());

        assertThat(elements()).isEqualTo(before);
        assertThat(fixture.openDocument.document().definitions()).isEmpty();
    }

    @Test
    void outsideWiresOnAConvertedPartReachTheInstancePin() {
        List<Component> parts = drawSwitchNotLed();
        Wire outside = wire(12, 2, 12, 8);
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));

        perform(fixture.features.actions().createComponent());

        CircuitDocument document = fixture.openDocument.document();
        Component instance = document.components().stream()
                .filter(component -> component.typeId().startsWith("subcircuit:"))
                .findFirst().orElseThrow();
        assertThat(document.wire(outside.id())).isPresent();
        Set<PinRef> pins = new NetlistCompiler(fixture.openDocument.registry()).compile(document)
                .netForWire(outside.id()).orElseThrow().pins();
        int outputIndex = indexOf(onlyDefinition().pinout(), "S");
        assertThat(pins).contains(new PinRef(instance.id(), outputIndex));
        assertThat(fixture.dialogs.messages).isEmpty();
        assertThat(document.component(parts.get(2).id())).contains(parts.get(2));
    }

    @Test
    void theKeptSwitchDrivesTheInstanceAndTheKeptLedShowsItsOutput() {
        List<Component> parts = drawSwitchNotLed();
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));

        perform(fixture.features.actions().createComponent());

        Component led = parts.get(2);
        // The switch starts at 1, so the LED shows 0 through the instance's NOT; toggling it lights the LED.
        waitUntil(() -> new VisibleState.LedState(Value.ZERO).equals(fixture.stateOf(led)));
        fixture.openDocument.interact(parts.get(0).id());
        waitUntil(() -> new VisibleState.LedState(Value.ONE).equals(fixture.stateOf(led)));
    }

    @Test
    void anUncheckedSwitchKeepsTheValueItShowsWhileSimulating() {
        Component input = drawSwitchNotLed().get(0);
        waitUntil(() -> new VisibleState.SwitchState(Value.ONE).equals(fixture.stateOf(input)));
        fixture.openDocument.interact(input.id());
        waitUntil(() -> new VisibleState.SwitchState(Value.ZERO).equals(fixture.stateOf(input)));
        fixture.dialogs.acceptForm(form -> {
            form.setName("Fijo");
            form.setConverted(input.id(), false);
        });

        perform(fixture.features.actions().createComponent());

        assertThat(onlyDefinition().body().components())
                .filteredOn(component -> component.typeId().equals(StandardComponentTypes.CONSTANT))
                .singleElement()
                .satisfies(constant -> assertThat(constant.parameters()).containsEntry("value", "0"));
        // The saved switch still says 1 (the default): the constant took the value it showed.
        assertThat(input.parameters()).containsEntry("value", "1");
    }

    private Component instance() {
        return fixture.openDocument.document().components().stream()
                .filter(component -> component.typeId().startsWith("subcircuit:"))
                .findFirst().orElseThrow();
    }

    @Test
    void aSelectionWithOnlyTestPartsOpensTheDialog() {
        Component clock = fixture.place(StandardComponentTypes.CLOCK, 0, 0, "CLK");
        Component led = fixture.place(StandardComponentTypes.LED, 1, -1, "");
        fixture.select(clock, led);
        fixture.dialogs.acceptForm(form -> form.setName("Parpadeo"));

        perform(fixture.features.actions().createComponent());

        assertThat(fixture.dialogs.messages).isEmpty();
        Map<String, Integer> widths = onlyDefinition().pinout().stream()
                .collect(Collectors.toMap(pin -> pin.spec().name(), pin -> pin.spec().width()));
        assertThat(widths).containsOnlyKeys("CLK", "Y1");
    }

    @Test
    void aSelectionWithNeitherPinsNorTestPartsStillExplainsWhy() {
        Component gate = fixture.place(StandardComponentTypes.NOT, 0, 0, "");
        fixture.select(gate);

        perform(fixture.features.actions().createComponent());

        assertThat(fixture.dialogs.messages).containsExactly(Messages.text("template.noInterfacePins"));
        assertThat(fixture.dialogs.forms).isEmpty();
    }

    private static int indexOf(List<DefinitionPin> pinout, String name) {
        List<String> names = new ArrayList<>();
        pinout.forEach(pin -> names.add(pin.spec().name()));
        return names.indexOf(name);
    }
}
