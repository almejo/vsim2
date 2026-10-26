package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ConvertedSelection;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.core.library.TestParts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ComponentFormTest {

    private final ElementId first = new ElementId("c1");
    private final ElementId second = new ElementId("c2");
    private final ElementId output = new ElementId("c3");
    private final PinOrder order = new PinOrder(List.of(first, second), List.of(output));
    private final Map<ElementId, String> names = Map.of(first, "a", second, "b", output, "y");

    @Test
    void okNeedsANameThatNoOtherComponentUses() {
        ComponentForm form = ComponentForm.create(order, names, List.of("Inversor"), Set.of("inversor"));
        assertThat(form.canAccept()).isFalse();
        assertThat(form.problem()).contains("Escribe un nombre para el componente.");
        form.setName("   ");
        assertThat(form.canAccept()).isFalse();
        form.setName("  INVERSOR ");
        assertThat(form.problem()).contains("Ya existe un componente llamado «INVERSOR».");
        form.setName(" Negador ");
        assertThat(form.canAccept()).isTrue();
        assertThat(form.result()).isEqualTo(new ComponentForm.Result("negador", "Negador", order, true));
    }

    @Test
    void theIdGetsASuffixWhenTheSlugIsTaken() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of("sumador", "sumador-2"));
        form.setName("Sumador");
        assertThat(form.result().definitionId()).isEqualTo("sumador-3");
    }

    @Test
    void pinsMoveWithinTheirSideAndListenersHear() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of());
        AtomicInteger changes = new AtomicInteger();
        form.addListener(changes::incrementAndGet);
        form.moveInput(0, 1);
        form.moveInput(1, 1);
        form.moveOutput(0, -1);
        assertThat(form.inputNames()).containsExactly("b", "a");
        assertThat(form.outputNames()).containsExactly("y");
        assertThat(changes).hasValue(1);
        form.setName("Par");
        assertThat(form.result().pinOrder()).isEqualTo(new PinOrder(List.of(second, first), List.of(output)));
    }

    @Test
    void updatingIgnoresTheTargetsOwnNameAndKeepsItsId() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter();
        ComponentForm form = ComponentForm.update(order, names, List.of("Inversor", "Doble inversor"),
                List.of(inverter, doubleInverter));
        assertThat(form.mode()).isEqualTo(ComponentForm.Mode.UPDATE);
        assertThat(form.name()).isEqualTo("Inversor");
        assertThat(form.canAccept()).isTrue();
        form.setName("doble INVERSOR");
        assertThat(form.canAccept()).isFalse();

        form.setTarget(doubleInverter);

        assertThat(form.name()).isEqualTo("Doble inversor");
        assertThat(form.result()).isEqualTo(new ComponentForm.Result(ExampleDefinitions.DOUBLE_INVERTER,
                "Doble inversor", order, false));
    }

    @Test
    void withoutTestPartsTheFormHasNoChoicesAndConvertsNothing() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of());
        form.setName("Par");

        assertThat(form.testParts()).isEmpty();
        assertThat(form.result().convertedParts()).isEmpty();
    }

    @Test
    void checkingATestPartAddsItsPinAndKeepsTheOrderTheUserChose() {
        ElementId switchId = new ElementId("c4");
        ElementId ledId = new ElementId("c5");
        ElementId switchPin = new ElementId("c10");
        ElementId ledPin = new ElementId("c11");
        List<TestParts.TestPart> parts = List.of(
                new TestParts.TestPart(switchId, StandardComponentTypes.SWITCH, TestParts.Kind.SWITCH, "", ""),
                new TestParts.TestPart(ledId, StandardComponentTypes.LED, TestParts.Kind.LED, "S", ""));
        List<Set<ElementId>> asked = new ArrayList<>();
        ComponentForm form = ComponentForm.create(parts, converted -> {
            asked.add(converted);
            List<ElementId> inputs = new ArrayList<>(List.of(first));
            List<ElementId> outputs = new ArrayList<>();
            Map<ElementId, String> pinNames = new HashMap<>(Map.of(first, "a"));
            if (converted.contains(switchId)) {
                inputs.add(switchPin);
                pinNames.put(switchPin, "B");
            }
            if (converted.contains(ledId)) {
                outputs.add(ledPin);
                pinNames.put(ledPin, "S");
            }
            return new ComponentForm.PinPlan(new PinOrder(inputs, outputs), pinNames);
        }, List.of(), Set.of());
        AtomicInteger changes = new AtomicInteger();
        form.addListener(changes::incrementAndGet);

        assertThat(form.testParts()).isEqualTo(parts);
        assertThat(form.isConverted(switchId)).isTrue();
        assertThat(form.isConverted(ledId)).isTrue();
        assertThat(form.inputNames()).containsExactly("a", "B");
        assertThat(form.outputNames()).containsExactly("S");

        form.moveInput(1, -1);
        form.setConverted(ledId, false);
        assertThat(form.outputNames()).isEmpty();
        assertThat(form.inputNames()).containsExactly("B", "a");
        form.setConverted(switchId, false);
        assertThat(form.inputNames()).containsExactly("a");
        form.setConverted(switchId, true);
        assertThat(form.inputNames()).containsExactly("a", "B");
        form.setConverted(switchId, true);

        assertThat(changes).hasValue(4);
        assertThat(asked.get(asked.size() - 1)).containsExactly(switchId);
        form.setName("Par");
        assertThat(form.result().convertedParts()).containsExactly(switchId);
        assertThat(form.result().pinOrder()).isEqualTo(new PinOrder(List.of(first, switchPin), List.of()));
    }

    @Test
    void renamedPinsShowTheirNewNameAndGoInTheResult() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of());
        form.setName("Par");
        AtomicInteger changes = new AtomicInteger();
        form.addListener(changes::incrementAndGet);

        form.renameInput(0, "Dato");
        form.renameOutput(0, " Salida ");

        assertThat(changes).hasValue(2);
        assertThat(form.inputNames()).containsExactly("Dato", "b");
        assertThat(form.outputNames()).containsExactly(" Salida ");
        assertThat(form.result().pinNames()).containsOnly(entry(first, "Dato"), entry(output, "Salida"));

        form.renameInput(0, "a"); // back to its default name: nothing to rename
        assertThat(form.result().pinNames()).containsOnly(entry(output, "Salida"));
    }

    @Test
    void aBlankOrRepeatedPinNameDisablesOkUntilItIsFixed() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of());
        form.setName("Par");

        form.renameInput(1, "   ");
        assertThat(form.canAccept()).isFalse();
        assertThat(form.problem()).contains("Escribe un nombre para cada pin.");
        assertThat(form.inputProblem(1)).contains("Escribe un nombre para cada pin.");
        assertThat(form.inputProblem(0)).isEmpty();

        form.renameInput(1, " Y"); // an input and an output: names are unique across sides, ignoring case
        assertThat(form.canAccept()).isFalse();
        assertThat(form.problem()).contains("Hay más de un pin llamado «Y».");
        assertThat(form.inputProblem(1)).contains("Hay más de un pin llamado «Y».");
        assertThat(form.outputProblem(0)).contains("Hay más de un pin llamado «y».");
        assertThat(form.inputProblem(0)).isEmpty();

        form.renameOutput(0, "Z");
        assertThat(form.canAccept()).isTrue();
        assertThat(form.result().pinNames()).containsOnly(entry(second, "Y"), entry(output, "Z"));
    }

    @Test
    void reorderingKeepsTheNewNames() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of());
        form.setName("Par");
        form.renameInput(0, "Dato");

        form.moveInput(0, 1);

        assertThat(form.inputNames()).containsExactly("b", "Dato");
        form.renameInput(0, "Habilitar");
        assertThat(form.inputNames()).containsExactly("Habilitar", "Dato");
        assertThat(form.result().pinNames()).containsOnly(entry(first, "Dato"), entry(second, "Habilitar"));
        assertThat(form.result().pinOrder()).isEqualTo(new PinOrder(List.of(second, first), List.of(output)));
    }

    @Test
    void aRenamedTestPartPinKeepsItsNameWhenTheOtherPartsChangeAndNamesItsRow() {
        ElementId switchId = new ElementId("c4");
        ElementId ledId = new ElementId("c5");
        ElementId switchPin = new ElementId("c10");
        ElementId ledPin = new ElementId("c11");
        List<TestParts.TestPart> parts = List.of(
                new TestParts.TestPart(switchId, StandardComponentTypes.SWITCH, TestParts.Kind.SWITCH, "", ""),
                new TestParts.TestPart(ledId, StandardComponentTypes.LED, TestParts.Kind.LED, "", ""));
        ComponentForm form = ComponentForm.create(parts, converted -> {
            List<ElementId> inputs = new ArrayList<>();
            List<ElementId> outputs = new ArrayList<>();
            Map<ElementId, String> pinNames = new HashMap<>();
            Map<ElementId, ConvertedSelection.PartPins> given = new HashMap<>();
            if (converted.contains(switchId)) {
                inputs.add(switchPin);
                pinNames.put(switchPin, "A");
                given.put(switchId, new ConvertedSelection.PartPins(List.of(switchPin), Optional.empty()));
            }
            if (converted.contains(ledId)) {
                outputs.add(ledPin);
                pinNames.put(ledPin, "Y1");
                given.put(ledId, new ConvertedSelection.PartPins(List.of(ledPin), Optional.empty()));
            }
            return new ComponentForm.PinPlan(new PinOrder(inputs, outputs), pinNames, given);
        }, List.of(), Set.of());
        form.setName("Negador");

        form.renameInput(0, "Entrada");
        form.renameOutput(0, "Salida");
        assertThat(form.outcome(switchId)).isEqualTo("Entrada (Entrada)");
        form.setConverted(ledId, false);
        form.setConverted(ledId, true);

        assertThat(form.inputNames()).containsExactly("Entrada");
        assertThat(form.outputNames()).containsExactly("Salida");
        assertThat(form.result().pinNames()).containsOnly(entry(switchPin, "Entrada"), entry(ledPin, "Salida"));
        form.setConverted(ledId, false);
        assertThat(form.result().pinNames()).containsOnly(entry(switchPin, "Entrada"));
    }
}
